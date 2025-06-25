package dev.markodojkic.legalcontractdigitizer.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractPartiesBalanceRequest {
	String contractAddress;
	String abi;
}
