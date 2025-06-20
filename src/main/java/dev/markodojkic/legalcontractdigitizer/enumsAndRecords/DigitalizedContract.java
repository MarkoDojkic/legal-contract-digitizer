package dev.markodojkic.legalcontractdigitizer.enumsAndRecords;

public record DigitalizedContract(
        String id,
        String userId,
        String contractText,
        String status
) {}