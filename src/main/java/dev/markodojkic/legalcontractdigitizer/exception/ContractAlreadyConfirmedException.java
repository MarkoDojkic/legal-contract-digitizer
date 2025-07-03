package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when an operation is attempted on a contract that has already been confirmed.
 */
public class ContractAlreadyConfirmedException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 7154669861082844049L;

	public ContractAlreadyConfirmedException(String message) {
		super(message);
	}
}