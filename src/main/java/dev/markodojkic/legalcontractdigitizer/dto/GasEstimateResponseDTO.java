package dev.markodojkic.legalcontractdigitizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GasEstimateResponseDTO {
	private String message;
	private BigInteger gas;
}