package dev.markodojkic.legalcontractdigitizer.model;

import java.util.List;

public record DeploymentRequestDTO(String contractId, String deployerWalletAddress, List<Object> constructorParams) {}
