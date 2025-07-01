package dev.markodojkic.legalcontractdigitizer.exception;

public class ClausesExtractionException extends RuntimeException {
	public ClausesExtractionException(String message) {
		super("Clauses extraction failed:\n" + message);
	}
}