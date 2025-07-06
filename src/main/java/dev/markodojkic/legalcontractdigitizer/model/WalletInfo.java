package dev.markodojkic.legalcontractdigitizer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * Represents wallet information including label, address, balance, and keystore file.
 *
 * @param label        The wallet label.
 * @param address      The wallet address.
 * @param balance      The balance in BigDecimal.
 * @param keystoreFile The keystore file path (ignored in JSON serialization).
 */
public record WalletInfo(String label, String address, BigDecimal balance, @JsonIgnore String keystoreFile) {
	@NotNull
	@Override
	public String toString() {
		return String.format("%s (%s) - Balance 🪙: %f Sepolia ETH", label, address, balance);
	}
}