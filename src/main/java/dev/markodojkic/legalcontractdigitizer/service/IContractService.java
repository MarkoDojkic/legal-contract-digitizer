package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.model.GasEstimateResponseDTO;
import dev.markodojkic.legalcontractdigitizer.model.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.model.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.exception.*;
import org.web3j.crypto.Credentials;

import java.util.List;

public interface IContractService {
	String saveUploadedContract(String contractText);
	void updateContractStatus(String deploymentAddress, ContractStatus newStatus) throws ContractNotFoundException, UnauthorizedAccessException;
	void deleteIfNotDeployed(String contractId) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, ContractAlreadyConfirmedException;
	DigitalizedContract getContract(String contractId) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException;
	List<DigitalizedContract> listContractsForUser();
	List<String> extractClauses(String contractText) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, ClausesExtractionException;
	String generateSolidity(String contractId) throws ContractNotFoundException, UnauthorizedAccessException, ClausesExtractionException, CompilationException, SolidityGenerationException;
	String deployContractWithParams(String contractId, List<Object> constructorParams, Credentials credentials) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, InvalidContractBinaryException, DeploymentFailedException;
	GasEstimateResponseDTO estimateGasForDeployment(String contractId, List<Object> constructorParams, String deployerWalletAddress) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, InvalidContractBinaryException, GasEstimationFailedException;
}