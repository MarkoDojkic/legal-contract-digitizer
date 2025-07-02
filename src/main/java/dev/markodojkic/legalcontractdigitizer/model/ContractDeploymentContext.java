package dev.markodojkic.legalcontractdigitizer.model;

import com.google.cloud.firestore.DocumentReference;

public record ContractDeploymentContext(EthereumContractContext ethContext, DocumentReference contractRef) {}
