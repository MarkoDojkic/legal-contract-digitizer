package dev.markodojkic.legalcontractdigitizer.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractFunctionRequestDTO {
	String functionName;
	List<Object> params;
	String valueWei;  // Send as String, convert to BigInteger in BE
	String requestedByWalletAddress;
}
