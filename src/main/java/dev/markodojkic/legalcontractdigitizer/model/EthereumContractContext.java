package dev.markodojkic.legalcontractdigitizer.model;

/**
 * Holds Ethereum contract binary and encoded constructor data.
 *
 * @param contractBinary     Compiled contract bytecode.
 * @param encodedConstructor ABI-encoded constructor parameters.
 */
public record EthereumContractContext(
        String contractBinary,
        String encodedConstructor
) {}