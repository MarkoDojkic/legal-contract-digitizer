package dev.markodojkic.legalcontractdigitizer.exception;

public class SolidityGenerationException extends RuntimeException {
	public SolidityGenerationException(String contractId, Throwable cause) {
		super("Failed to generate Solidity contract for contract ID: " + contractId, cause);
	}
}