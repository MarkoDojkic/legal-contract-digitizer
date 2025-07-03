package dev.markodojkic.legalcontractdigitizer.model;

import com.google.cloud.firestore.DocumentReference;

/**
 * Holds context information necessary for deploying a contract to Ethereum.
 *
 * @param ethContext  Ethereum-specific contract deployment context (binary, encoded constructor).
 * @param contractRef Firestore document reference for the contract metadata.
 */
public record ContractDeploymentContext(EthereumContractContext ethContext, DocumentReference contractRef) {}