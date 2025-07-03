package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when an invalid function call is made on a contract.
 */
public class InvalidFunctionCallException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = -3606647312649801167L;

	public InvalidFunctionCallException(String message) {
		super(message);
	}
}