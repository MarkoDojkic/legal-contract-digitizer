package dev.markodojkic.legalcontractdigitizer.model;

public enum ContractStatus {
	UPLOADED,
	CLAUSES_EXTRACTED,
	SOLIDITY_PREPARED,
	SOLIDITY_GENERATED,
	DEPLOYED,
	CONFIRMED,
	TERMINATED
}