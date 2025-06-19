package dev.markodojkic.legalcontractdigitizer.util;

public record DigitalizedContract(
        String id,
        String userId,
        String contractText,
        String status
) {}