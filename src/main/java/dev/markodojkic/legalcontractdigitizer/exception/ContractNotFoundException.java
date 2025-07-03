package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when a contract is not found in the system.
 */
public class ContractNotFoundException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 1218635428572432370L;

	public ContractNotFoundException(String message) {
		super(message);
	}
}