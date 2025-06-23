package dev.markodojkic.legalcontractdigitizer.controller;

import dev.markodojkic.legalcontractdigitizer.dto.*;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.service.ContractServiceImpl;
import dev.markodojkic.legalcontractdigitizer.service.FileTextExtractorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contract API", description = "Endpoints for contract processing and deployment")
@Slf4j
public class ContractController {

	private final ContractServiceImpl contractService;
	private final FileTextExtractorService fileTextExtractorService;

	@Operation(summary = "Upload a contract file")
	@PostMapping("/upload")
	public ResponseEntity<UploadResponseDTO> uploadContract(@RequestParam("file") MultipartFile file) {
		try {
			String contractId = contractService.saveUploadedContract(fileTextExtractorService.extractText(file));
			return ResponseEntity.ok(
					UploadResponseDTO.builder()
							.message("Contract uploaded successfully. ID: " + contractId)
							.build()
			);
		} catch (Exception e) {
			log.error("Upload failed", e);
			return ResponseEntity.internalServerError()
					.body(UploadResponseDTO.builder().message("Failed to upload contract.").build());
		}
	}

	@Operation(summary = "List all contracts for the authenticated user")
	@GetMapping("/list")
	public ResponseEntity<List<DigitalizedContract>> listUserContracts(@RequestParam String userId) {
		List<DigitalizedContract> contracts = contractService.listContractsForUser(userId);
		return ResponseEntity.ok(contracts);
	}


	@Operation(summary = "Get contract")
	@GetMapping("/{id}")
	public ResponseEntity<DigitalizedContract> getContract(@PathVariable String id) {
		return ResponseEntity.ok(contractService.getContract(id));
	}

	@Operation(summary = "Extract legal clauses using LLM")
	@PostMapping("/extract-clauses")
	public ResponseEntity<ClauseExtractionResponseDTO> extractClauses(@RequestParam String contractId) {
		List<String> clauses = contractService.extractClauses(contractId);
		return ResponseEntity.ok(new ClauseExtractionResponseDTO(clauses));
	}

	@Operation(summary = "Generate Solidity smart contract")
	@PostMapping("/generate-solidity")
	public ResponseEntity<String> generateSolidity(@RequestParam String contractId) {
		String soliditySource = contractService.generateSolidity(contractId);
		return ResponseEntity.ok(soliditySource);
	}

	@Operation(summary = "Deploy contract to Ethereum testnet")
	@PostMapping("/deploy-contract")
	public ResponseEntity<String> deployContract(@RequestBody DeploymentRequestDTO request) {
		log.info("Deploying contract with id: {}", request.getContractId());

		if (request.getContractId() == null || request.getContractId().isEmpty()) {
			return ResponseEntity.badRequest().body("Contract ID is required");
		}

		try {
			String contractAddress = contractService.deployContractWithParams(
					request.getContractId(),
					request.getConstructorParams()
			);
			return ResponseEntity.ok("Contract deployed at address: " + contractAddress);
		} catch (Exception e) {
			log.error("Contract deployment failed: {}", e.getMessage());
			return ResponseEntity.status(500).body("Deployment failed: " + e.getMessage());
		}
	}
}