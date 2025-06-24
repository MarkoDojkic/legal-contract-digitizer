package dev.markodojkic.legalcontractdigitizer.exception;

public class CompilationException extends RuntimeException {
	public CompilationException(String contractId, Throwable cause) {
		super("Solidity compilation failed for contract ID: " + contractId, cause);
	}
}