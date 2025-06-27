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
public class ContractAddressGettersRequestDTO {
    private String contractAddress;
    private List<String> getterFunctions;
}