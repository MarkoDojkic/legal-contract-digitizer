package dev.markodojkic.legalcontractdigitizer.controller;

import dev.markodojkic.legalcontractdigitizer.dto.DeploymentRequestDTO;
import dev.markodojkic.legalcontractdigitizer.dto.GasEstimateResponseDTO;
import dev.markodojkic.legalcontractdigitizer.exception.*;
import dev.markodojkic.legalcontractdigitizer.service.EthereumService;
import dev.markodojkic.legalcontractdigitizer.service.impl.ContractServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;

@RestController
@RequestMapping("/api/v1/ethereum")
@RequiredArgsConstructor
@Tag(name = "Ethereum API", description = "Endpoints for Ethereum smart contract operations")
@Slf4j
public class EthereumController {

	private final EthereumService ethereumService;
	private final ContractServiceImpl contractService;

	@Operation(summary = "Deploy contract to Ethereum testnet",
			description = "Deploys a compiled contract with given constructor parameters.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Contract deployed successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid input or contract state"),
			@ApiResponse(responseCode = "403", description = "Unauthorized or contract not found"),
			@ApiResponse(responseCode = "500", description = "Deployment failed due to server error")
	})
	@PostMapping("/deploy-contract")
	public ResponseEntity<String> deployContract(
			@RequestBody
			@io.swagger.v3.oas.annotations.parameters.RequestBody(
					description = "Deployment request with contract ID and constructor params",
					required = true)
			DeploymentRequestDTO request) {
		if (request == null || request.getContractId() == null || request.getContractId().isBlank()) {
			log.warn("Deploy contract request missing contractId");
			return ResponseEntity.badRequest().body("Contract ID is required");
		}
		try {
			log.info("Deploying contract with id: {}", request.getContractId());
			String contractAddress = contractService.deployContractWithParams(
					request.getContractId(),
					request.getConstructorParams()
			);
			return ResponseEntity.ok("Contract deployed at address: " + contractAddress);
		} catch (ContractNotFoundException | UnauthorizedAccessException e) {
			log.warn("Unauthorized or contract not found: {}", e.getMessage());
			return ResponseEntity.status(403).body(e.getMessage());
		} catch (InvalidContractBinaryException | IllegalStateException e) {
			log.warn("Invalid contract state or data: {}", e.getMessage());
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (DeploymentFailedException e) {
			log.error("Deployment failed", e);
			return ResponseEntity.internalServerError().body("Deployment failed: " + e.getMessage());
		}
	}

	@Operation(summary = "Estimate gas required to deploy a contract",
			description = "Estimates the gas cost for deploying the contract with given parameters.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Gas estimation succeeded"),
			@ApiResponse(responseCode = "400", description = "Invalid input or contract state"),
			@ApiResponse(responseCode = "403", description = "Unauthorized or contract not found"),
			@ApiResponse(responseCode = "500", description = "Gas estimation failed due to server error")
	})
	@PostMapping("/estimate-gas")
	public ResponseEntity<GasEstimateResponseDTO> estimateGas(
			@RequestBody
			@io.swagger.v3.oas.annotations.parameters.RequestBody(
					description = "Gas estimation request with contract ID and constructor params",
					required = true)
			DeploymentRequestDTO request) {
		if (request == null || request.getContractId() == null || request.getContractId().isBlank()) {
			log.warn("Estimate gas request missing contractId");
			return ResponseEntity.badRequest()
					.body(new GasEstimateResponseDTO("Contract ID is required", null));
		}
		try {
			log.info("Estimating gas for contract id: {}", request.getContractId());
			BigInteger gas = contractService.estimateGasForDeployment(
					request.getContractId(),
					request.getConstructorParams()
			);
			return ResponseEntity.ok(new GasEstimateResponseDTO("OK", gas));
		} catch (ContractNotFoundException | UnauthorizedAccessException e) {
			log.warn("Unauthorized or contract not found: {}", e.getMessage());
			return ResponseEntity.status(403)
					.body(new GasEstimateResponseDTO(e.getMessage(), null));
		} catch (InvalidContractBinaryException | IllegalStateException e) {
			log.warn("Invalid contract state or data: {}", e.getMessage());
			return ResponseEntity.badRequest()
					.body(new GasEstimateResponseDTO(e.getMessage(), null));
		} catch (GasEstimationFailedException e) {
			log.error("Gas estimation failed", e);
			return ResponseEntity.internalServerError()
					.body(new GasEstimateResponseDTO("Error estimating gas: " + e.getMessage(), null));
		}
	}

	@Operation(summary = "Check if contract is confirmed (code exists at address)",
			description = "Returns true if the Ethereum contract is deployed and confirmed at the address.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Confirmation status returned"),
			@ApiResponse(responseCode = "400", description = "Invalid Ethereum address"),
			@ApiResponse(responseCode = "500", description = "Failed to check confirmation status")
	})
	@GetMapping("/{address}/confirmed")
	public ResponseEntity<Boolean> isContractConfirmed(
			@Parameter(description = "Ethereum contract address to check", required = true)
			@PathVariable String address) {
		if (address == null || address.isBlank()) {
			log.warn("Check contract confirmed called with empty address");
			return ResponseEntity.badRequest().build();
		}
		try {
			boolean confirmed = ethereumService.isContractConfirmed(address);
			return ResponseEntity.ok(confirmed);
		} catch (InvalidEthereumAddressException e) {
			log.warn("Invalid contract address: {}", e.getMessage());
			return ResponseEntity.badRequest().build();
		} catch (EthereumConnectionException e) {
			log.error("Failed to check contract confirmation for address {}", address, e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@Operation(summary = "Get transaction receipt by transaction hash",
			description = "Returns the JSON representation of the transaction receipt, if available.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Transaction receipt returned"),
			@ApiResponse(responseCode = "204", description = "Transaction receipt not found yet"),
			@ApiResponse(responseCode = "400", description = "Invalid transaction hash"),
			@ApiResponse(responseCode = "500", description = "Failed to retrieve transaction receipt")
	})
	@GetMapping("/transaction/{txHash}/receipt")
	public ResponseEntity<String> getTransactionReceipt(
			@Parameter(description = "Transaction hash to query", required = true)
			@PathVariable String txHash) {
		if (txHash == null || txHash.isBlank()) {
			log.warn("Get transaction receipt called with empty txHash");
			return ResponseEntity.badRequest().body("Transaction hash is required");
		}
		try {
			String receiptJson = ethereumService.getTransactionReceipt(txHash);
			if (receiptJson == null) {
				return ResponseEntity.noContent().build();
			}
			return ResponseEntity.ok(receiptJson);
		} catch (IllegalArgumentException e) {
			log.warn("Invalid transaction hash: {}", e.getMessage());
			return ResponseEntity.badRequest().body("Invalid transaction hash: " + e.getMessage());
		} catch (EthereumConnectionException e) {
			log.error("Failed to get transaction receipt for hash {}", txHash, e);
			return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
		}
	}
}