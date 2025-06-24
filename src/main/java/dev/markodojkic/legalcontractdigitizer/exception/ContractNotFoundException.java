package dev.markodojkic.legalcontractdigitizer.exception;

public class ContractNotFoundException extends RuntimeException {
	public ContractNotFoundException(String message) {
		super(message);
	}
}