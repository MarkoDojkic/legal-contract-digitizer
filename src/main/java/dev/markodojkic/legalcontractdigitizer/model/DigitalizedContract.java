package dev.markodojkic.legalcontractdigitizer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;

import java.util.List;

@Builder
public record DigitalizedContract(
		String id,
		@JsonIgnore
		String userId,
		@JsonIgnore
		String contractText,
		ContractStatus status,
		List<String> extractedClauses,
		String soliditySource,
		@JsonIgnore
		String binary,
		String abi,
		String deployedAddress
) {}