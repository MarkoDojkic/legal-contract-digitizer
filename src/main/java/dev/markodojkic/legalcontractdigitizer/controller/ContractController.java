package dev.markodojkic.legalcontractdigitizer.controller;

import dev.markodojkic.legalcontractdigitizer.dto.ClauseExtractionResponseDTO;
import dev.markodojkic.legalcontractdigitizer.dto.UploadResponseDTO;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.exception.ClausesExtractionException;
import dev.markodojkic.legalcontractdigitizer.exception.CompilationException;
import dev.markodojkic.legalcontractdigitizer.exception.ContractNotFoundException;
import dev.markodojkic.legalcontractdigitizer.exception.SolidityGenerationException;
import dev.markodojkic.legalcontractdigitizer.service.FileTextExtractorService;
import dev.markodojkic.legalcontractdigitizer.service.impl.ContractServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contract API", description = "Endpoints for managing contracts and generating Solidity code")
@Slf4j
public class ContractController {

	private final ContractServiceImpl contractService;
	private final FileTextExtractorService fileTextExtractorService;

	@Operation(summary = "Upload a contract file")
	@ApiResponse(responseCode = "200", description = "Contract uploaded successfully")
	@ApiResponse(responseCode = "500", description = "Internal server error during upload")
	@PostMapping("/upload")
	public ResponseEntity<UploadResponseDTO> uploadContract(@RequestParam("file") MultipartFile file) {
		try {
			String contractId = contractService.saveUploadedContract(fileTextExtractorService.extractText(file));
			return ResponseEntity.ok(
					UploadResponseDTO.builder().message("Contract uploaded successfully. ID: " + contractId).build()
			);
		} catch (Exception e) {
			log.error("Upload failed", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(UploadResponseDTO.builder().message("Failed to upload contract").build());
		}
	}

	@Operation(summary = "Get contract by ID")
	@ApiResponse(responseCode = "200", description = "Contract retrieved successfully")
	@ApiResponse(responseCode = "404", description = "Contract not found")
	@GetMapping("/{id}")
	public ResponseEntity<DigitalizedContract> getContract(@PathVariable String id) {
		return ResponseEntity.ok(contractService.getContract(id));
	}

	@Operation(summary = "List all contracts for a user")
	@ApiResponse(responseCode = "200", description = "Contracts listed successfully")
	@GetMapping("/list")
	public ResponseEntity<List<DigitalizedContract>> listUserContracts(@RequestParam String userId) {
		return ResponseEntity.ok(contractService.listContractsForUser(userId));
	}

	@Operation(summary = "Extract legal clauses from contract")
	@ApiResponse(responseCode = "200", description = "Clauses extracted successfully")
	@ApiResponse(responseCode = "400", description = "Invalid contract ID or no contract found")
	@PatchMapping("/extract-clauses")
	public ResponseEntity<ClauseExtractionResponseDTO> extractClauses(@RequestParam String contractId) {
		try {
			List<String> clauses = contractService.extractClauses(contractId);
			return ResponseEntity.ok(new ClauseExtractionResponseDTO(clauses));
		} catch (ContractNotFoundException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ClauseExtractionResponseDTO(Collections.singletonList("Contract not found") ));
		} catch (ClausesExtractionException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ClauseExtractionResponseDTO(Collections.singletonList("Failed to extract clauses")));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ClauseExtractionResponseDTO(Collections.singletonList("Unknown error occurred")));
		}
	}

	@Operation(summary = "Generate Solidity code from contract")
	@ApiResponse(responseCode = "200", description = "Solidity generated successfully")
	@ApiResponse(responseCode = "400", description = "Contract invalid or not eligible for generation")
	@PatchMapping("/generate-solidity")
	public ResponseEntity<String> generateSolidity(@RequestParam String contractId) {
		try {
			String soliditySource = contractService.generateSolidity(contractId);
			return ResponseEntity.ok(soliditySource);
		} catch (ContractNotFoundException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Contract not found");
		} catch (SolidityGenerationException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to generate Solidity contract");
		} catch (CompilationException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Solidity compilation failed");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unknown error occurred");
		}
	}

	@Operation(summary = "Delete contract if not confirmed")
	@ApiResponse(responseCode = "204", description = "Contract deleted successfully")
	@ApiResponse(responseCode = "409", description = "Contract already confirmed")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteContract(@PathVariable("id") String contractId) {
		try {
			contractService.deleteIfNotConfirmed(contractId);
			return ResponseEntity.noContent().build();
		} catch (IllegalStateException e) {
			log.warn("Attempted to delete confirmed contract: {}", contractId);
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		} catch (Exception e) {
			log.error("Failed to delete contract {}", contractId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}
}