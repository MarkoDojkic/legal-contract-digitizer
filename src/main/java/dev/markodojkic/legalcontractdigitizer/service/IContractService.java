package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.model.GasEstimateResponseDTO;
import dev.markodojkic.legalcontractdigitizer.model.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.model.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.exception.*;
import org.web3j.crypto.Credentials;

import java.util.List;

/**
 * Service interface to manage legal contracts lifecycle including upload, status update,
 * clause extraction, Solidity generation, deployment, and gas estimation.
 */
public interface IContractService {

	/**
	 * Saves a newly uploaded contract text.
	 *
	 * @param contractText the raw contract text to save
	 * @return the unique identifier of the saved contract
	 */
	String saveUploadedContract(String contractText);

	/**
	 * Updates the status of a contract identified by its deployment address.
	 *
	 * @param deploymentAddress the Ethereum address where the contract is deployed
	 * @param newStatus the new contract status to set
	 * @throws ContractNotFoundException if the contract is not found
	 * @throws UnauthorizedAccessException if the caller is unauthorized to update the contract
	 */
	void updateContractStatus(String deploymentAddress, ContractStatus newStatus)
			throws ContractNotFoundException, UnauthorizedAccessException;

	/**
	 * Deletes the contract if it has not been deployed yet.
	 *
	 * @param contractId the unique ID of the contract to delete
	 * @throws ContractNotFoundException if the contract is not found
	 * @throws UnauthorizedAccessException if the caller is unauthorized
	 * @throws ContractReadException if reading the contract data fails
	 * @throws ContractAlreadyConfirmedException if the contract has already been confirmed
	 */
	void deleteIfNotDeployed(String contractId)
			throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, ContractAlreadyConfirmedException;

	/**
	 * Retrieves the full digitalized contract by its ID.
	 *
	 * @param contractId the unique ID of the contract
	 * @return the digitalized contract object
	 * @throws ContractNotFoundException if the contract is not found
	 * @throws UnauthorizedAccessException if the caller is unauthorized to access the contract
	 * @throws ContractReadException if reading contract data fails
	 */
	DigitalizedContract getContract(String contractId)
			throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException;

	/**
	 * Lists all contracts associated with the currently authenticated user.
	 *
	 * @return list of digitalized contracts for the user
	 */
	List<DigitalizedContract> listContractsForUser();

	/**
	 * Extracts contract clauses from raw contract text.
	 *
	 * @param contractText raw text of the contract
	 * @return list of extracted clauses
	 * @throws ContractNotFoundException if related contract is not found
	 * @throws UnauthorizedAccessException if the caller is unauthorized
	 * @throws ContractReadException if reading contract fails
	 * @throws ClausesExtractionException if clause extraction fails
	 */
	List<String> extractClauses(String contractText)
			throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, ClausesExtractionException;

	/**
	 * Generates Solidity source code for the contract identified by ID.
	 *
	 * @param contractId the contract's unique identifier
	 * @return Solidity source code as a string
	 * @throws ContractNotFoundException if the contract is not found
	 * @throws UnauthorizedAccessException if unauthorized access
	 * @throws ClausesExtractionException if clause extraction fails
	 * @throws CompilationException if compilation of Solidity code fails
	 * @throws SolidityGenerationException if code generation fails
	 */
	String generateSolidity(String contractId)
			throws ContractNotFoundException, UnauthorizedAccessException, ClausesExtractionException, CompilationException, SolidityGenerationException;

	/**
	 * Deploys the contract with given constructor parameters using provided Ethereum credentials.
	 *
	 * @param contractId the ID of the contract to deploy
	 * @param constructorParams list of constructor parameters
	 * @param credentials Ethereum credentials (wallet)
	 * @return the deployed contract's Ethereum address
	 * @throws ContractNotFoundException if the contract is not found
	 * @throws UnauthorizedAccessException if unauthorized access
	 * @throws ContractReadException if contract reading fails
	 * @throws InvalidContractBinaryException if contract binary is invalid
	 * @throws DeploymentFailedException if deployment fails
	 */
	String deployContractWithParams(String contractId, List<Object> constructorParams, Credentials credentials)
			throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, InvalidContractBinaryException, DeploymentFailedException;

	/**
	 * Estimates gas needed for deploying the contract with given constructor parameters.
	 *
	 * @param contractId the contract ID
	 * @param constructorParams constructor parameters list
	 * @param deployerWalletAddress wallet address deploying the contract
	 * @return gas estimation response DTO containing gas price and limit
	 * @throws ContractNotFoundException if contract not found
	 * @throws UnauthorizedAccessException if unauthorized access
	 * @throws ContractReadException if contract reading fails
	 * @throws InvalidContractBinaryException if contract binary is invalid
	 * @throws GasEstimationFailedException if gas estimation fails
	 */
	GasEstimateResponseDTO estimateGasForDeployment(String contractId, List<Object> constructorParams, String deployerWalletAddress)
			throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, InvalidContractBinaryException, GasEstimationFailedException;
}