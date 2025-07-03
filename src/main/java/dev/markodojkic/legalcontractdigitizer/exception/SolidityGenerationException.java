package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when Solidity code preparation or generation fails.
 */
public class SolidityGenerationException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = -2344317267583321144L;

	public SolidityGenerationException(String message) {
		super("Solidity code preparation/generation failed: " + message);
	}
}