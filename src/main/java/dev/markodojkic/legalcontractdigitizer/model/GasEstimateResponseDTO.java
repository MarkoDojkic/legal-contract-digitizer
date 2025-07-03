package dev.markodojkic.legalcontractdigitizer.model;

import java.math.BigInteger;

/**
 * Response DTO for gas estimate information.
 *
 * @param message      Message regarding the gas estimate.
 * @param gasPriceWei  Gas price in Wei.
 * @param gasLimit     Gas limit estimate with safety margin (multiplied by 2).
 */
public record GasEstimateResponseDTO(
        String message,
        BigInteger gasPriceWei,
        BigInteger gasLimit
) {}