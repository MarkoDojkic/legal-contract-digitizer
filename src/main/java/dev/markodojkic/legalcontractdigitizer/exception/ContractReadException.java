package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when reading a contract fails.
 */
public class ContractReadException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 2662143177123653718L;

	public ContractReadException(String message) {
		super(message);
	}
}