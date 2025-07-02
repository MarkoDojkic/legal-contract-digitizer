package dev.markodojkic.legalcontractdigitizer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record WalletInfo(String label, String address, BigDecimal balance, @JsonIgnore String keystoreFile) {
	@NotNull
	@Override
	public String toString() {
		return String.format("%s (%s) - Balance \uD83E\uDE99: %f Sepolia ETH", label, address, balance);
	}
}