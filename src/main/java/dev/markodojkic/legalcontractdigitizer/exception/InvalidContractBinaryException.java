package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when the contract binary is invalid or malformed.
 */
public class InvalidContractBinaryException extends Exception {
	@Serial
	private static final long serialVersionUID = -2568514613302719912L;

	public InvalidContractBinaryException(String message) {
		super(message);
	}
}