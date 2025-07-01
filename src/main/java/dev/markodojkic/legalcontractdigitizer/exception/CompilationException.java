package dev.markodojkic.legalcontractdigitizer.exception;

public class CompilationException extends RuntimeException {
	public CompilationException(String message) {
		super("Solidity compilation failed:\n" + message);
	}
}