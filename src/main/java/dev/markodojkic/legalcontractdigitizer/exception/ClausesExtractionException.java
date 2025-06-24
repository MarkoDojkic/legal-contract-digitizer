package dev.markodojkic.legalcontractdigitizer.exception;

public class ClausesExtractionException extends RuntimeException {
	public ClausesExtractionException(String contractId, Throwable cause) {
		super("Failed to extract clauses from contract ID: " + contractId, cause);
	}
}