package dev.markodojkic.legalcontractdigitizer.controller;

import dev.markodojkic.legalcontractdigitizer.exception.*;
import dev.markodojkic.legalcontractdigitizer.model.*;
import dev.markodojkic.legalcontractdigitizer.service.IContractService;
import dev.markodojkic.legalcontractdigitizer.service.IEthereumService;
import dev.markodojkic.legalcontractdigitizer.service.IEthereumWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;

import static dev.markodojkic.legalcontractdigitizer.model.ContractStatus.CONFIRMED;

@RestController
@RequestMapping("/api/v1/ethereum")
@Tag(name = "Ethereum API", description = "Endpoints for Ethereum network and smart contract related operations")
@ApiResponse(responseCode = "401", description = "Unauthorized access")
@RequiredArgsConstructor
public class EthereumController {

	private final IEthereumService ethereumService;
	private final IContractService contractService;
	private final IEthereumWalletService ethereumWalletService;

	@PostMapping("/register")
	@Operation(summary = "Register a new Ethereum wallet", description = "Creates new Ethereum wallet on chain and stores it's credentials in json file.", responses = {@ApiResponse(responseCode = "200", description = "Returns newly registered wallet info"), @ApiResponse(responseCode = "500", description = "Internal server error during registration of new Ethereum wallet")})
	public ResponseEntity<WalletInfo> registerWallet(@Parameter(description = "New wallet identifier", required = true) @RequestParam String label) {
		try {
			return ResponseEntity.ok(ethereumWalletService.createWallet(label));
		} catch (WalletCreationException e) {
			return ResponseEntity.internalServerError().body(new WalletInfo(e.getLocalizedMessage(), null, null, null));
		}
	}

	@Operation(summary = "List all registered wallets", description = "Reads all JSON files and returns data related to them (identifier, address and if retrieved balance).", responses = @ApiResponse(responseCode = "200", description = "Returns newly registered wallet info"))
	@GetMapping("/getAvailableWallets")
	public ResponseEntity<List<WalletInfo>> listWallets() {
		return ResponseEntity.ok(ethereumWalletService.listWallets());
	}

	@Operation(summary = "Deploy contract to Ethereum testnet", description = "Deploys a compiled contract with given constructor parameters.", responses = {@ApiResponse(responseCode = "200", description = "Contract deployed successfully"), @ApiResponse(responseCode = "400", description = "Invalid input or contract state"), @ApiResponse(responseCode = "403", description = "Unauthorized access to contract"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "500", description = "Deployment failed due to server error")})
	@PostMapping("/deploy-contract")
	public ResponseEntity<String> deployContract(@RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Deployment request with contract ID and constructor params", required = true) DeploymentRequestDTO request) {
		try {
			return ResponseEntity.ok("Contract deployed at address: " + contractService.deployContractWithParams(request.contractId(), request.constructorParams(), ethereumWalletService.loadCredentials(request.deployerWalletAddress())));
		} catch (UnauthorizedAccessException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getLocalizedMessage());
		} catch (ContractNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getLocalizedMessage());
		} catch (ContractReadException | InvalidContractBinaryException e) {
			return ResponseEntity.badRequest().body(e.getLocalizedMessage());
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
		}
	}

	@Operation(summary = "Estimate gas required to deploy a contract", description = "Estimates the gas cost for deploying the contract with given parameters.", responses = {@ApiResponse(responseCode = "200", description = "Gas estimation succeeded"), @ApiResponse(responseCode = "400", description = "Invalid input or contract state"), @ApiResponse(responseCode = "403", description = "Unauthorized access to contract"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "500", description = "Gas estimation failed due to server error")})
	@PostMapping("/estimate-gas")
	public ResponseEntity<GasEstimateResponseDTO> estimateGas(@RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Gas estimation request with contract ID and constructor params", required = true) DeploymentRequestDTO request) {

		try {
			return ResponseEntity.ok(contractService.estimateGasForDeployment(request.contractId(), request.constructorParams(), request.deployerWalletAddress()));
		} catch (UnauthorizedAccessException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GasEstimateResponseDTO(e.getLocalizedMessage(), null, null));
		} catch (ContractNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new GasEstimateResponseDTO(e.getLocalizedMessage(), null, null));
		} catch (InvalidContractBinaryException e) {
			return ResponseEntity.badRequest().body(new GasEstimateResponseDTO(e.getLocalizedMessage(), null, null));
		} catch (GasEstimationFailedException e) {
			return ResponseEntity.internalServerError().body(new GasEstimateResponseDTO(e.getLocalizedMessage(), null, null));
		}
	}

	@Operation(summary = "Check if smart contract exists (code exists at address and isDestroyed flag is false)", description = "Returns true if the Ethereum contract is deployed, confirmed at the address and not destroyed.", responses = {@ApiResponse(responseCode = "200", description = "Existence status returned"), @ApiResponse(responseCode = "400", description = "Invalid Ethereum address"), @ApiResponse(responseCode = "403", description = "Unauthorized access to contract"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "500", description = "Failed to check confirmation status")})
	@GetMapping("/{address}/exists")
	public ResponseEntity<String> doesSmartContractExist(@Parameter(description = "Ethereum contract address to check", required = true) @PathVariable String address) {

		try {
			boolean exists = ethereumService.doesSmartContractExist(address);
			if (exists) contractService.updateContractStatus(address, CONFIRMED);
			return ResponseEntity.ok().body(String.valueOf(exists));
		} catch (UnauthorizedAccessException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getLocalizedMessage());
		} catch (ContractNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getLocalizedMessage());
		} catch (InvalidEthereumAddressException e) {
			return ResponseEntity.badRequest().build();
		} catch (Exception e) {
			return ResponseEntity.internalServerError().build();
		}
	}

	@Operation(summary = "Get transaction receipt by transaction hash", description = "Returns the JSON representation of the transaction receipt, if available.", responses = {@ApiResponse(responseCode = "200", description = "Transaction receipt returned"), @ApiResponse(responseCode = "204", description = "Transaction receipt not found yet"), @ApiResponse(responseCode = "400", description = "Invalid transaction hash"), @ApiResponse(responseCode = "500", description = "Failed to retrieve transaction receipt")})
	@GetMapping("/transaction/{txHash}/receipt")
	public ResponseEntity<String> getTransactionReceipt(@Parameter(description = "Transaction hash to query", required = true) @PathVariable String txHash) {

		try {
			String receiptJson = ethereumService.getTransactionReceipt(txHash);
			if (receiptJson == null) return ResponseEntity.noContent().build();
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(receiptJson);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getLocalizedMessage());
		} catch (EthereumConnectionException e) {
			return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
		}
	}

	@Operation(summary = "Get balance of smart contract (in ETH)", responses = {@ApiResponse(responseCode = "200", description = "Balance returned successfully"), @ApiResponse(responseCode = "400", description = "Invalid Ethereum address", content = @Content), @ApiResponse(responseCode = "500", description = "Ethereum RPC error", content = @Content)})
	@GetMapping("/{address}/balance")
	public ResponseEntity<String> getContractBalance(@Parameter(description = "Ethereum contract address to retrieve balance from", required = true) @PathVariable String address) {
		try {
			return ResponseEntity.ok(ethereumService.getBalance(address).toString());
		} catch (InvalidEthereumAddressException e) {
			return ResponseEntity.badRequest().body(e.getLocalizedMessage());
		} catch (EthereumConnectionException e) {
			return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
		}
	}

	@Operation(summary = "Invoke a function on deployed smart contract", responses = {@ApiResponse(responseCode = "200", description = "Function invoked successfully, returns txHash"), @ApiResponse(responseCode = "400", description = "Invalid input or address", content = @Content), @ApiResponse(responseCode = "500", description = "Function invocation failed", content = @Content)})
	@PostMapping("/{address}/invoke")
	public ResponseEntity<String> invokeContractFunction(@Parameter(description = "Smart contract address", required = true) @PathVariable String address, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Invoking contract function related parameters", required = true) @RequestBody ContractFunctionRequestDTO request) {
		try {
			return ResponseEntity.ok(ethereumService.invokeFunction(address, request.functionName(), request.params(), new BigInteger(request.valueWei()), ethereumWalletService.loadCredentials(request.requestedByWalletAddress())));
		} catch (InvalidEthereumAddressException e) {
			return ResponseEntity.badRequest().body(e.getLocalizedMessage());
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
		}
	}

	@Operation(summary = "Resolve contract parties address data", responses = @ApiResponse(responseCode = "200", description = "Successfully resolved the contract parties address data"))
	@PostMapping("/resolveContractPartiesAddressData")
	public ResponseEntity<ContractPartiesAddressDataResponseDTO> resolveContractPartiesAddressData(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of required parties names and ethereum addresses", required = true) @RequestBody ContractPartiesAddressDataRequestDTO req) {
		return ResponseEntity.ok(new ContractPartiesAddressDataResponseDTO(ethereumService.resolveContractPartiesAddressData(req.contractAddress(), req.getterFunctions())));
	}
}