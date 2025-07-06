package dev.markodojkic.legalcontractdigitizer.model;

import java.util.List;

/**
 * DTO representing a request to invoke a function on a smart contract.
 *
 * @param functionName               The name of the contract function to invoke.
 * @param params                     Parameters to pass to the function (must match ABI types).
 * @param valueWei                  Value in Wei to send with the function call (as a string to avoid JSON encoding issues).
 * @param requestedByWalletAddress The wallet address of the caller requesting the invocation.
 */
public record ContractFunctionRequestDTO(String functionName, List<Object> params, String valueWei, String requestedByWalletAddress) {}