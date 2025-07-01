package dev.markodojkic.legalcontractdigitizer.controller;

import dev.markodojkic.legalcontractdigitizer.enums_records.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.exception.CompilationException;
import dev.markodojkic.legalcontractdigitizer.exception.ContractAlreadyConfirmedException;
import dev.markodojkic.legalcontractdigitizer.exception.ContractNotFoundException;
import dev.markodojkic.legalcontractdigitizer.service.FileTextExtractorService;
import dev.markodojkic.legalcontractdigitizer.service.IContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contract API", description = "Endpoints for managing contracts and generating Solidity code")
@Slf4j
public class ContractController {

	private final IContractService contractService;
	private final FileTextExtractorService fileTextExtractorService;

	@Operation(summary = "Upload a contract file")
	@ApiResponse(responseCode = "200", description = "Contract uploaded successfully")
	@ApiResponse(responseCode = "500", description = "Internal server error during upload")
	@PostMapping("/upload")
	public ResponseEntity<String> uploadContract(@RequestParam("file") MultipartFile file) {
		try {
			String contractId = contractService.saveUploadedContract(fileTextExtractorService.extractText(file));
			return ResponseEntity.ok("Contract uploaded successfully. ID: " + contractId);
		} catch (Exception e) {
			log.error("Contract Upload failed", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Failed to upload contract:\n" + e.getLocalizedMessage());
		}
	}

	@Operation(summary = "Get contract by ID")
	@ApiResponse(responseCode = "200", description = "Contract retrieved successfully")
	@ApiResponse(responseCode = "404", description = "Contract not found")
	@ApiResponse(responseCode = "500", description = "Internal server error")
	@GetMapping("/{id}")
	public ResponseEntity<?> getContract(@PathVariable String id) {
		try {
			DigitalizedContract contract = contractService.getContract(id);
			return ResponseEntity.ok(contract);
		} catch (ContractNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getLocalizedMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(e.getLocalizedMessage());
		}
	}

	@Operation(summary = "List all contracts for a user")
	@ApiResponse(responseCode = "200", description = "Contracts listed successfully")
	@ApiResponse(responseCode = "500", description = "Internal server error")
	@GetMapping("/list")
	public ResponseEntity<?> listUserContracts(@RequestParam String userId) {
		try {
			List<DigitalizedContract> contracts = contractService.listContractsForUser(userId);
			return ResponseEntity.ok(contracts);
		} catch (Exception e) {
			log.error("Error listing contracts for userId={}", userId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(e.getLocalizedMessage());
		}
	}


	@Operation(summary = "Extract legal clauses from contract")
	@ApiResponse(responseCode = "200", description = "Clauses extracted successfully")
	@ApiResponse(responseCode = "400", description = "No contract found")
	@ApiResponse(responseCode = "500", description = "Server error occurred")
	@PatchMapping("/extract-clauses")
	public ResponseEntity<String> extractClauses(@RequestParam String contractId) {
		try {
			return ResponseEntity.ok("Clauses extracted successfully (Count: " + contractService.extractClauses(contractId).size() + ")");
		} catch (ContractNotFoundException _) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Contract not found");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getLocalizedMessage());
		}
	}

	@Operation(summary = "Generate Solidity code from contract")
	@ApiResponse(responseCode = "200", description = "Solidity generated successfully")
	@ApiResponse(responseCode = "206", description = "Solidity prepared but not generated due compilation error")
	@ApiResponse(responseCode = "400", description = "No contract found")
	@ApiResponse(responseCode = "500", description = "Server error occurred")
	@PatchMapping("/generate-solidity")
	public ResponseEntity<String> generateSolidity(@RequestParam String contractId) {
		try {
			return ResponseEntity.ok(contractService.generateSolidity(contractId));
		} catch (CompilationException compilationException){
			return 	ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(compilationException.getLocalizedMessage());
		} catch (ContractNotFoundException _) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Contract not found");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getLocalizedMessage());
		}
	}

	@Operation(summary = "Delete contract if not confirmed")
	@ApiResponse(responseCode = "204", description = "Contract deleted successfully")
	@ApiResponse(responseCode = "409", description = "Contract already confirmed")
	@DeleteMapping("/{id}")
	public ResponseEntity<String> deleteContract(@PathVariable("id") String contractId) {
		try {
			contractService.deleteIfNotDeployed(contractId);
			return ResponseEntity.noContent().build();
		} catch (ContractAlreadyConfirmedException _) {
			log.warn("Attempted to delete confirmed contract: {}", contractId);
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		} catch (Exception e) {
			log.error("Failed to delete contract {}", contractId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getLocalizedMessage());
		}
	}
}