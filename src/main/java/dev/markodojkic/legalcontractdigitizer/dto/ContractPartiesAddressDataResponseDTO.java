package dev.markodojkic.legalcontractdigitizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractPartiesAddressDataResponseDTO {
    private Map<String, String> resolvedAddresses;
}
