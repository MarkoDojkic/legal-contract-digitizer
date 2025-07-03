package dev.markodojkic.legalcontractdigitizer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;

import java.util.List;

/**
 * Represents a digitalized legal contract including metadata, status, and Solidity sources.
 *
 * @param id              Unique identifier of the digitalized contract.
 * @param userId          (Ignored in JSON) ID of the user who owns the contract.
 * @param contractText    (Ignored in JSON) Raw text of the contract.
 * @param status          Current status of the contract.
 * @param extractedClauses List of extracted clauses from the contract text.
 * @param soliditySource  Generated Solidity source code of the contract.
 * @param binary          (Ignored in JSON) Compiled contract binary.
 * @param abi             ABI definition of the contract.
 * @param deployedAddress Ethereum address where the contract is deployed.
 */
@Builder
public record DigitalizedContract(
		String id,
		@JsonIgnore String userId,
		@JsonIgnore String contractText,
		ContractStatus status,
		List<String> extractedClauses,
		String soliditySource,
		@JsonIgnore String binary,
		String abi,
		String deployedAddress
) {}