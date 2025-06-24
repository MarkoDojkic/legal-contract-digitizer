package dev.markodojkic.legalcontractdigitizer.enums_records;

public record EthereumContractContext(
		String contractBinary,
		String encodedConstructor
) {}