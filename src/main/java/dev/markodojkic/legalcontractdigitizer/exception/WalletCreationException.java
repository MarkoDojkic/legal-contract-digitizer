package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when wallet creation fails.
 */
public class WalletCreationException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 2007885122771232615L;

	public WalletCreationException(String message) {
		super(message);
	}
}