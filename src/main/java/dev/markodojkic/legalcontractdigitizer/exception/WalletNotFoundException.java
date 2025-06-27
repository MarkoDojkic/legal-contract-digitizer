package dev.markodojkic.legalcontractdigitizer.exception;

public class WalletNotFoundException extends RuntimeException {
	public WalletNotFoundException(String address) {
		super("Wallet not found for address: " + address);
	}
}
