package dev.markodojkic.legalcontractdigitizer.exception;

public class ContractAlreadyConfirmedException extends RuntimeException {
	public ContractAlreadyConfirmedException(String message) {
		super(message);
	}
}