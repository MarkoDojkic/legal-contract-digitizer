package dev.markodojkic.legalcontractdigitizer.exception;

public class InvalidEthereumAddressException extends RuntimeException {
	public InvalidEthereumAddressException(String msg) {
		super(msg);
	}
}