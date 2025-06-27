package dev.markodojkic.legalcontractdigitizer.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletInfo {
	private String label;
	private String address;
	private BigDecimal balance;
	@JsonIgnore
	private String keystoreFile;

	@Override
	public String toString() {
		return String.format("%s (%s) - %f Sepolia ETH", label, address, balance);
	}
}