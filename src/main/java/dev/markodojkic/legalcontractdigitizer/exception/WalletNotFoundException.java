package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when a wallet is not found for the specified address.
 */
public class WalletNotFoundException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = -105548839863137719L;

	public WalletNotFoundException(String message) {
		super(message);
	}
}