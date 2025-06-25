package dev.markodojkic.legalcontractdigitizer.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyBalanceDto {
	String roleName;

	String address;

	BigDecimal balanceEth;
}