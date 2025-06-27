package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.enums_records.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.exception.*;
import org.web3j.crypto.Credentials;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public interface IContractService {
	String saveUploadedContract(String contractText);
	DigitalizedContract getContract(String contractId);
	List<String> extractClauses(String contractText);
	String generateSolidity(String contractId);
	String deployContractWithParams(String contractId, List<Object> constructorParams, Credentials credentials) throws ContractNotFoundException, UnauthorizedAccessException,
			IllegalStateException, InvalidContractBinaryException, DeploymentFailedException;
	BigDecimal estimateGasForDeployment(String contractId, List<Object> constructorParams, String deployerWalletAddress) throws ContractNotFoundException, UnauthorizedAccessException,
			IllegalStateException, InvalidContractBinaryException, GasEstimationFailedException;
	List<DigitalizedContract> listContractsForUser(String userId);
	void updateContractStatusToConfirmed(String deploymentAddress) throws ContractNotFoundException, UnauthorizedAccessException, IllegalStateException;
	void deleteIfNotConfirmed(String contractId);
}