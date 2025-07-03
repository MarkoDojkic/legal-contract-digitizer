package dev.markodojkic.legalcontractdigitizer.model;

/**
 * Enum representing the lifecycle status of a contract.
 */
public enum ContractStatus {
	UPLOADED,
	CLAUSES_EXTRACTED,
	SOLIDITY_PREPARED,
	SOLIDITY_GENERATED,
	DEPLOYED,
	CONFIRMED,
	TERMINATED
}