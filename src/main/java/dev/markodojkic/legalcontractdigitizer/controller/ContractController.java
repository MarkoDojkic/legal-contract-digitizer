package dev.markodojkic.legalcontractdigitizer.controller;

import dev.markodojkic.legalcontractdigitizer.exception.CompilationException;
import dev.markodojkic.legalcontractdigitizer.exception.ContractAlreadyConfirmedException;
import dev.markodojkic.legalcontractdigitizer.exception.ContractNotFoundException;
import dev.markodojkic.legalcontractdigitizer.exception.UnauthorizedAccessException;
import dev.markodojkic.legalcontractdigitizer.model.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.service.IContractService;
import dev.markodojkic.legalcontractdigitizer.util.Either;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "Contract API", description = "Endpoints for managing contracts and generating solidity code")
@ApiResponse(responseCode = "401", description = "Unauthorized access")
@RequiredArgsConstructor
public class ContractController {

	private final Tika tika = new Tika();
	private final IContractService contractService;

	@Operation(summary = "Upload a contract file", description = "Uploads a legal contract file and returns its generated ID.", responses = {@ApiResponse(responseCode = "200", description = "Contract uploaded successfully"), @ApiResponse(responseCode = "500", description = "Internal server error during upload")})
	@PostMapping("/upload")
	public ResponseEntity<String> uploadContract(@Parameter(description = "Contract file to upload", required = true) @RequestParam("file") MultipartFile file) {
		try {
			return ResponseEntity.ok("Contract uploaded successfully. ID: " + contractService.saveUploadedContract(tika.parseToString(file.getInputStream())));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload contract:\n" + e.getLocalizedMessage());
		}
	}

	@Operation(summary = "Get contract by ID", description = "Retrieves a contract by its unique ID.", responses = {@ApiResponse(responseCode = "200", description = "Contract retrieved successfully"), @ApiResponse(responseCode = "403", description = "Unauthorized access to contract"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "500", description = "Internal server error")})
	@GetMapping("/{id}")
	public ResponseEntity<Either<DigitalizedContract, String>> getContract(@Parameter(description = "ID of the contract to retrieve", required = true) @PathVariable String id) {
		try {
			return ResponseEntity.ok(Either.left(contractService.getContract(id)));
		} catch (UnauthorizedAccessException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Either.right(e.getLocalizedMessage()));
		} catch (ContractNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Either.right(e.getLocalizedMessage()));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Either.right(e.getLocalizedMessage()));
		}
	}

	@Operation(summary = "List all contracts for a user", description = "Returns a list of all contracts associated with the current user.", responses = {@ApiResponse(responseCode = "200", description = "Contracts listed successfully"), @ApiResponse(responseCode = "500", description = "Internal server error")})
	@GetMapping("/list")
	public ResponseEntity<Either<List<DigitalizedContract>, String>> listUserContracts() {
		try {
			return ResponseEntity.ok(Either.left(contractService.listContractsForUser()));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Either.right(e.getLocalizedMessage()));
		}
	}

	@Operation(summary = "Extract legal clauses from contract", description = "Extracts legal clauses from a contract by its ID.", responses = {@ApiResponse(responseCode = "200", description = "Clauses extracted successfully"), @ApiResponse(responseCode = "403", description = "Unauthorized access to contract"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "500", description = "Server error occurred")})
	@PatchMapping("/extract-clauses")
	public ResponseEntity<String> extractClauses(@Parameter(description = "ID of the contract to extract clauses from", required = true) @RequestParam String contractId) {
		try {
			return ResponseEntity.ok("Clauses extracted successfully (Count: " + contractService.extractClauses(contractId).size() + ")");
		} catch (UnauthorizedAccessException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getLocalizedMessage());
		} catch (ContractNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getLocalizedMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getLocalizedMessage());
		}
	}

	@Operation(summary = "Generate solidity code from contract", description = "Generates Solidity smart contract code from a legal contract.", responses = {@ApiResponse(responseCode = "200", description = "Solidity generated successfully"), @ApiResponse(responseCode = "206", description = "Solidity prepared but not generated due to compilation error"), @ApiResponse(responseCode = "403", description = "Unauthorized access to contract"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "500", description = "Server error occurred")})
	@PatchMapping("/generate-solidity")
	public ResponseEntity<String> generateSolidity(@Parameter(description = "ID of the contract to generate Solidity from", required = true) @RequestParam String contractId) {
		try {
			return ResponseEntity.ok(contractService.generateSolidity(contractId));
		} catch (CompilationException e) {
			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(e.getLocalizedMessage());
		} catch (UnauthorizedAccessException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getLocalizedMessage());
		} catch (ContractNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getLocalizedMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getLocalizedMessage());
		}
	}

	@Operation(summary = "Edit solidity code for contract", description = "Edits prepared Solidity smart contract for a legal contract.", responses = { @ApiResponse(responseCode = "200", description = "Solidity code edited successfully"), @ApiResponse(responseCode = "403", description = "Unauthorized access to contract"), @ApiResponse(responseCode = "404", description = "Contract not found"), @ApiResponse(responseCode = "500", description = "Server error occurred") })
	@PatchMapping("/edit-solidity")
	public ResponseEntity<String> editSolidity(@RequestBody @Parameter(description = "DigitalizedContract object with id and updated Solidity source only", required = true) DigitalizedContract updatedDigitalizedContract) {
		try {
			contractService.editSolidity(updatedDigitalizedContract.id(), updatedDigitalizedContract.soliditySource());
			return ResponseEntity.noContent().build();
		} catch (UnauthorizedAccessException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getLocalizedMessage());
		} catch (ContractNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getLocalizedMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getLocalizedMessage());
		}
	}


	@Operation(summary = "Delete contract if not confirmed", description = "Deletes a contract by ID if it has not been confirmed/deployed.", responses = {@ApiResponse(responseCode = "204", description = "Contract deleted successfully"), @ApiResponse(responseCode = "403", description = "Unauthorized access to contract"), @ApiResponse(responseCode = "409", description = "Contract already confirmed"), @ApiResponse(responseCode = "500", description = "Internal server error")})
	@DeleteMapping("/{id}")
	public ResponseEntity<String> deleteContract(@Parameter(description = "ID of the contract to delete", required = true) @PathVariable("id") String contractId) {
		try {
			contractService.deleteIfNotDeployed(contractId);
			return ResponseEntity.noContent().build();
		} catch (UnauthorizedAccessException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getLocalizedMessage());
		} catch (ContractAlreadyConfirmedException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getLocalizedMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getLocalizedMessage());
		}
	}
}