package dev.markodojkic.legalcontractdigitizer.model;

import java.util.List;

public record ContractPartiesAddressDataRequestDTO(String contractAddress, List<String> getterFunctions) {}