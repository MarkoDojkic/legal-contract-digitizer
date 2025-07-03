package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when gas estimation for a transaction fails.
 */
public class GasEstimationFailedException extends Exception {
	@Serial
	private static final long serialVersionUID = -17817667755983010L;

	public GasEstimationFailedException(String message) {
		super(message);
	}
}