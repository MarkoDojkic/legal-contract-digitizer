package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when an Ethereum address provided is invalid.
 */
public class InvalidEthereumAddressException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = -4162195038463003416L;

	public InvalidEthereumAddressException(String message) {
		super(message);
	}
}