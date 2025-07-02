package dev.markodojkic.legalcontractdigitizer.exception;

public class SolidityGenerationException extends RuntimeException {
	public SolidityGenerationException(String message) {
		super("Solidity code preparation/generation failed: " + message);
	}
}