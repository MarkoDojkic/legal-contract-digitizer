package dev.markodojkic.legalcontractdigitizer.exception;

import java.io.Serial;

/**
 * Exception thrown when Solidity compilation fails.
 */
public class CompilationException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 1539818982832302676L;

	public CompilationException(String message) {
		super("Solidity compilation failed:\n" + message);
	}
}