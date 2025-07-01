package dev.markodojkic.legalcontractdigitizer.exception;

public class InvalidFunctionCallException extends RuntimeException {
	public InvalidFunctionCallException(String msg, Throwable t) {
		super(msg, t);
	}
}