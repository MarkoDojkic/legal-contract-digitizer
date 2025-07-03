package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when clause extraction from a contract fails.
 */
public class ClausesExtractionException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = -5628671479615725082L;

	public ClausesExtractionException(String message) {
		super("Clauses extraction failed:\n" + message);
	}
}
