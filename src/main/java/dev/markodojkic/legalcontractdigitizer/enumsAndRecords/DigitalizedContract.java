package dev.markodojkic.legalcontractdigitizer.enumsAndRecords;

import lombok.Builder;

import java.util.List;

@Builder
public record DigitalizedContract(
		String id,
		String userId,
		String contractText,
		ContractStatus status,
		List<String> extractedClauses,
		String soliditySource,
		String binary,
		String abi,
		String deployedAddress
) {}