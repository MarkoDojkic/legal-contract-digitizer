package dev.markodojkic.legalcontractdigitizer.model;

import java.util.Map;

/**
 * DTO representing the response containing resolved contract party addresses.
 *
 * @param resolvedAddresses A map of function names to their resolved Ethereum addresses.
 */
public record ContractPartiesAddressDataResponseDTO(Map<String, String> resolvedAddresses) {}