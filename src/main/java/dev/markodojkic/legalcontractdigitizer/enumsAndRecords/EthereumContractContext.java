package dev.markodojkic.legalcontractdigitizer.enumsAndRecords;

public record EthereumContractContext(
		String contractBinary,
		String encodedConstructor
) {}