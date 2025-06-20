package dev.markodojkic.legalcontractdigitizer.enumsAndRecords;

import com.google.cloud.firestore.DocumentReference;

public record ContractDeploymentContext(
		String userId,
		EthereumContractContext ethContext,
		DocumentReference contractRef
) {}
