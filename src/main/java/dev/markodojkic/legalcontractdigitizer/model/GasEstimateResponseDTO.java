package dev.markodojkic.legalcontractdigitizer.model;

import java.math.BigInteger;

public record GasEstimateResponseDTO(String message, BigInteger gasPriceWei, BigInteger gasLimit) {}