package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when a user tries to access a resource without proper authorization.
 */
public class UnauthorizedAccessException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 2719256571762038788L;

	public UnauthorizedAccessException(String message) {
		super(message);
	}
}