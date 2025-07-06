package dev.markodojkic.legalcontractdigitizer.model;

import java.util.List;

/**
 * DTO for requesting party addresses from a smart contract using specified getter functions.
 *
 * @param contractAddress The deployed contract's address.
 * @param getterFunctions List of getter function names to call for retrieving party addresses.
 */
public record ContractPartiesAddressDataRequestDTO(String contractAddress, List<String> getterFunctions) {}