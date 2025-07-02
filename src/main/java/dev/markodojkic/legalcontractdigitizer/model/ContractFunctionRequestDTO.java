package dev.markodojkic.legalcontractdigitizer.model;

import java.util.List;

/**
 * @param valueWei Send as String (to avoid json encoding issues), convert to BigInteger in BE
 */
public record ContractFunctionRequestDTO(String functionName, List<Object> params, String valueWei, String requestedByWalletAddress) {}