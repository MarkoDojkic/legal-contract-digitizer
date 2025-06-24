package dev.markodojkic.legalcontractdigitizer.enums_records;

import com.google.cloud.firestore.DocumentReference;

public record ContractDeploymentContext(
		String userId,
		EthereumContractContext ethContext,
		DocumentReference contractRef
) {}
