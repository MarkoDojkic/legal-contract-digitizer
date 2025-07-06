package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when there is a connection failure with the Ethereum node.
 */
public class EthereumConnectionException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = -8308994506186586639L;

	public EthereumConnectionException(String message) {
		super(message);
	}
}