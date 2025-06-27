package dev.markodojkic.legalcontractdigitizer.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractFunctionRequestDTO {
	String abi;

	String functionName;

	List<Object> params;

	BigInteger valueWei;

	String requestedByWalletAddress;
}
