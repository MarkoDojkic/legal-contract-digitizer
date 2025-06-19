package dev.markodojkic.legalcontractdigitizer.controller;

import dev.markodojkic.legalcontractdigitizer.dto.*;
import dev.markodojkic.legalcontractdigitizer.service.ContractServiceImpl;
import dev.markodojkic.legalcontractdigitizer.service.EthereumService;
import dev.markodojkic.legalcontractdigitizer.service.FileTextExtractorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Legal Contract Digitizer API", description = "Endpoints for legal contract processing and blockchain deployment")
@Slf4j
public class MainController {

	private final ContractServiceImpl contractService;
	private final EthereumService ethereumService;
	private final FileTextExtractorService fileTextExtractorService;

	@Operation(summary = "Upload a contract file")
	@PostMapping("/upload-contract")
	public ResponseEntity<UploadResponseDTO> uploadContract(@RequestParam("file") MultipartFile file) {
		try {
			String contractId = contractService.saveUploadedContract(fileTextExtractorService.extractText(file));
			return ResponseEntity.ok(
					UploadResponseDTO.builder()
							.message("Contract uploaded successfully. ID: " + contractId)
							.build()
			);
		} catch (Exception e) {
			return ResponseEntity.internalServerError()
					.body(UploadResponseDTO.builder().message("Failed to upload contract.").build());
		}
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

	@Operation(summary = "Check contract deployment status")
	@GetMapping("/contract-status/{id}")
	public ResponseEntity<DeploymentStatusResponseDTO> getContractStatus(@PathVariable String id) {
		DeploymentStatusResponseDTO response = contractService.getContractStatus(id);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Fetch Ethereum transaction receipt by hash")
	@GetMapping("/contract-tx/{txHash}")
	public ResponseEntity<String> getTransactionReceipt(@PathVariable String txHash) {
		try {
			String receiptJson = ethereumService.getTransactionReceipt(txHash);
			if (receiptJson == null) {
				return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Pending or not found.");
			}
			return ResponseEntity.ok(receiptJson);
		} catch (Exception e) {
			log.error("Error fetching transaction receipt for {}: {}", txHash, e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Failed to fetch receipt: " + e.getMessage());
		}
	}
}