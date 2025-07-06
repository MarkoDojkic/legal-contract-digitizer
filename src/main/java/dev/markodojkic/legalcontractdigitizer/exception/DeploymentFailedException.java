package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when contract deployment to the Ethereum network fails.
 */
public class DeploymentFailedException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 4684439662950305054L;

	public DeploymentFailedException(String message) {
		super(message);
	}
}