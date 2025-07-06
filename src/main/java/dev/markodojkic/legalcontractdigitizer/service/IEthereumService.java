package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.model.EthereumContractContext;
import dev.markodojkic.legalcontractdigitizer.exception.*;
import org.apache.commons.lang3.tuple.Pair;
import org.web3j.crypto.Credentials;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Service interface for interacting with Ethereum blockchain.
 * Handles contract deployment, gas estimation, invocation, and other Ethereum-related operations.
 */
public interface IEthereumService {

	/**
	 * Prepares the deployment context by encoding constructor parameters.
	 *
	 * @param binary compiled contract binary
	 * @param constructorParams constructor parameters list
	 * @return an EthereumContractContext containing binary and encoded constructor data
	 * @throws InvalidContractBinaryException if the binary is invalid
	 */
	EthereumContractContext buildDeploymentContext(String binary, List<Object> constructorParams) throws InvalidContractBinaryException;

	/**
	 * Deploys a compiled smart contract to Ethereum using given credentials.
	 *
	 * @param binary compiled contract binary
	 * @param encodedConstructor encoded constructor parameters
	 * @param credentials credentials for deployment
	 * @return the deployed contract's Ethereum address
	 * @throws DeploymentFailedException if deployment fails
	 */
	String deployCompiledContract(String binary, String encodedConstructor, Credentials credentials) throws DeploymentFailedException;

	/**
	 * Estimates gas price and gas limit for deploying a contract.
	 *
	 * @param binary compiled contract binary
	 * @param encodedConstructor encoded constructor parameters
	 * @param deployerWalletAddress address deploying the contract
	 * @return pair of gas price and gas limit
	 * @throws GasEstimationFailedException if estimation fails
	 * @throws InvalidContractBinaryException if binary is invalid
	 */
	Pair<BigInteger, BigInteger> estimateGasForDeployment(String binary, String encodedConstructor, String deployerWalletAddress) throws GasEstimationFailedException, InvalidContractBinaryException;

	/**
	 * Checks if a smart contract exists at the specified address.
	 *
	 * @param contractAddress Ethereum address of the contract
	 * @return true if contract exists, false otherwise
	 * @throws InvalidEthereumAddressException if the address is invalid
	 * @throws EthereumConnectionException if connection to Ethereum fails
	 */
	boolean doesSmartContractExist(String contractAddress) throws InvalidEthereumAddressException, EthereumConnectionException;

	/**
	 * Retrieves the transaction receipt for a given transaction hash.
	 *
	 * @param txHash transaction hash
	 * @return transaction receipt as a string (or JSON)
	 * @throws IllegalArgumentException if txHash is invalid
	 * @throws EthereumConnectionException if connection fails
	 */
	String getTransactionReceipt(String txHash) throws IllegalArgumentException, EthereumConnectionException;

	/**
	 * Retrieves the balance of an Ethereum address.
	 *
	 * @param address Ethereum address
	 * @return balance as BigDecimal in ETH
	 * @throws InvalidEthereumAddressException if address is invalid
	 * @throws EthereumConnectionException if connection fails
	 */
	BigDecimal getBalance(String address) throws InvalidEthereumAddressException, EthereumConnectionException;

	/**
	 * Invokes a smart contract function.
	 *
	 * @param contractAddress address of the contract
	 * @param functionName name of the function to call
	 * @param params list of parameters for the function
	 * @param valueWei amount of Wei to send with the call
	 * @param credentials credentials to sign the transaction
	 * @return transaction hash of the invocation
	 * @throws InvalidEthereumAddressException if contract address is invalid
	 * @throws InvalidFunctionCallException if the function call is invalid
	 * @throws GasEstimationFailedException if gas estimation fails
	 */
	String invokeFunction(String contractAddress, String functionName, List<Object> params, BigInteger valueWei, Credentials credentials) throws InvalidEthereumAddressException, InvalidFunctionCallException, GasEstimationFailedException;

	/**
	 * Resolves contract parties' addresses by calling specified getter functions on the contract.
	 *
	 * @param contractAddress address of the contract
	 * @param getterFunctions list of getter function names
	 * @return map of function names to resolved Ethereum addresses
	 */
	Map<String, String> resolveContractPartiesAddressData(String contractAddress, List<String> getterFunctions);
}