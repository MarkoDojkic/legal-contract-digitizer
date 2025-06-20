package dev.markodojkic.legalcontractdigitizer.controller;

import dev.markodojkic.legalcontractdigitizer.dto.DeploymentRequestDTO;
import dev.markodojkic.legalcontractdigitizer.dto.GasEstimateResponseDTO;
import dev.markodojkic.legalcontractdigitizer.service.ContractServiceImpl;
import dev.markodojkic.legalcontractdigitizer.service.EthereumService;
import io.swagger.v3.oas.annotations.Operation;
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

	@Operation(summary = "Estimate gas required to deploy a contract")
	@PostMapping("/estimate-gas")
	public ResponseEntity<GasEstimateResponseDTO> estimateGas(@RequestBody DeploymentRequestDTO request) {
		log.info("Estimating gas for contract id: {}", request.getContractId());

		if (request.getContractId() == null || request.getContractId().isEmpty()) {
			return ResponseEntity.badRequest().body(
					new GasEstimateResponseDTO("Contract ID is required", null)
			);
		}

		try {
			BigInteger gas = contractService.estimateGasForDeployment(
					request.getContractId(),
					request.getConstructorParams()
			);
			return ResponseEntity.ok(new GasEstimateResponseDTO("OK", gas));
		} catch (Exception e) {
			log.error("Gas estimation failed: {}", e.getMessage());
			return ResponseEntity.internalServerError().body(
					new GasEstimateResponseDTO("Error estimating gas: " + e.getMessage(), null)
			);
		}
	}

	@Operation(summary = "Check if contract is confirmed (code exists at address)")
	@GetMapping("/contract/{address}/confirmed")
	public ResponseEntity<Boolean> isContractConfirmed(@PathVariable String address) {
		try {
			boolean confirmed = ethereumService.isContractConfirmed(address);
			return ResponseEntity.ok(confirmed);
		} catch (Exception e) {
			log.error("Failed to check contract confirmation for address {}: {}", address, e.getMessage());
			return ResponseEntity.badRequest().build();
		}
	}

	@Operation(summary = "Get transaction receipt by transaction hash")
	@GetMapping("/transaction/{txHash}/receipt")
	public ResponseEntity<String> getTransactionReceipt(@PathVariable String txHash) {
		try {
			String receiptJson = ethereumService.getTransactionReceipt(txHash);
			if (receiptJson == null) {
				return ResponseEntity.noContent().build(); // transaction not mined yet
			}
			return ResponseEntity.ok(receiptJson);
		} catch (Exception e) {
			log.error("Failed to get transaction receipt for hash {}: {}", txHash, e.getMessage());
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}
}
