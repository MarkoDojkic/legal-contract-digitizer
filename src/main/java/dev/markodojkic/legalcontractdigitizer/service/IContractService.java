package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.DigitalizedContract;

import java.math.BigInteger;
import java.util.List;

public interface IContractService {
	String saveUploadedContract(String contractText);
	DigitalizedContract getContract(String contractId);
	List<String> extractClauses(String contractText);
	String generateSolidity(String contractId);
	String deployContractWithParams(String contractId, List<Object> constructorParams);
	BigInteger estimateGasForDeployment(String contractId, List<Object> constructorParams);
	List<DigitalizedContract> listContractsForUser(String userId);
}