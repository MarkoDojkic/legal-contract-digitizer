package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.enums_records.EthereumContractContext;
import dev.markodojkic.legalcontractdigitizer.exception.*;

import java.math.BigInteger;
import java.util.List;

public interface EthereumService {
	EthereumContractContext buildDeploymentContext(String binary, List<Object> constructorParams) throws InvalidContractBinaryException;

	String deployCompiledContract(String binary, String encodedConstructor) throws DeploymentFailedException;

	BigInteger estimateGasForDeployment(String binary, String encodedConstructor) throws GasEstimationFailedException;

	boolean isContractConfirmed(String contractAddress) throws InvalidEthereumAddressException, EthereumConnectionException;

	String getTransactionReceipt(String txHash) throws IllegalArgumentException, EthereumConnectionException;
}