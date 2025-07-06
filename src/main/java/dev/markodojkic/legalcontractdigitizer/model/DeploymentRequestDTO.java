package dev.markodojkic.legalcontractdigitizer.model;

import java.util.List;

/**
 * DTO representing a request to deploy a smart contract.
 *
 * @param contractId           Identifier of the contract to deploy.
 * @param deployerWalletAddress Ethereum wallet address initiating the deployment.
 * @param constructorParams    Parameters to pass to the contract constructor.
 */
public record DeploymentRequestDTO(String contractId, String deployerWalletAddress, List<Object> constructorParams) {}