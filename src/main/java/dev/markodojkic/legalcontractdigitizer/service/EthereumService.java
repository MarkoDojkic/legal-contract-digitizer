package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.dto.ContractPartiesBalanceRequest;
import dev.markodojkic.legalcontractdigitizer.dto.PartyBalanceDto;
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

	BigInteger getBalance(String address) throws InvalidEthereumAddressException, EthereumConnectionException;

	String invokeFunction(
			String contractAddress,
			String abiJson,
			String functionName,
			List<Object> params,
			BigInteger valueWei
	) throws InvalidEthereumAddressException, InvalidFunctionCallException, EthereumConnectionException;

	List<PartyBalanceDto> getContractPartiesBalances(ContractPartiesBalanceRequest request) throws ContractReadException;
}