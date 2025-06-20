package dev.markodojkic.legalcontractdigitizer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.EthereumContractContext;
import dev.markodojkic.legalcontractdigitizer.util.Web3jTypeUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EthereumService {

	private static final Pattern HEX_PATTERN = Pattern.compile("^0x[0-9a-fA-F]+$");

	private Web3j web3j;
	private TransactionManager transactionManager;
	private Credentials credentials;

	@Value("${ethereum.rpc.url}")
	private String ethereumRpcUrl;

	@Value("${ethereum.private.key}")
	private String privateKey;

	@Value("${ethereum.chain.id:1337}")
	private long chainId;

	@PostConstruct
	public void init() {
		try {
			OkHttpClient okHttpClient = new OkHttpClient.Builder()
					.connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
					.readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
					.build();

			HttpService httpService = new HttpService(ethereumRpcUrl, okHttpClient, false);

			web3j = Web3j.build(httpService);
			credentials = Credentials.create(privateKey);
			transactionManager = new RawTransactionManager(web3j, credentials, chainId);

			log.info("EthereumService initialized with RPC: {}, ChainId: {}", ethereumRpcUrl, chainId);
		} catch (Exception e) {
			log.error("EthereumService initialization failed", e);
			throw new IllegalStateException(e);
		}
	}

	public EthereumContractContext buildDeploymentContext(String binary, List<Object> constructorParams) {
		if (StringUtils.isBlank(binary)) {
			throw new IllegalArgumentException("Contract binary cannot be null or empty");
		}

		List<Type> abiTypes = Web3jTypeUtil.convertToAbiTypes(constructorParams);
		String encodedConstructor = FunctionEncoder.encodeConstructor(abiTypes);

		return new EthereumContractContext(binary, encodedConstructor);
	}

	/**
	 * Deploy contract with encoded constructor data.
	 *
	 * Uses dynamic gas price and gas limit estimation.
	 *
	 * @param binary Compiled contract bytecode (hex string without 0x)
	 * @param encodedConstructor ABI-encoded constructor parameters hex string (without 0x prefix)
	 * @return deployed contract address
	 * @throws Exception if deployment fails
	 */
	public String deployCompiledContract(String binary, String encodedConstructor) throws Exception {
		if (StringUtils.isBlank(binary)) {
			throw new IllegalArgumentException("Contract binary cannot be null or empty");
		}

		if (encodedConstructor == null) {
			encodedConstructor = "";
		}

		String data = "0x" + binary + encodedConstructor;

		// Estimate gas limit dynamically
		BigInteger estimatedGasLimit = estimateGasForDeployment(binary, encodedConstructor);
		log.info("Estimated gas limit for deployment: {}", estimatedGasLimit);

		BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

		RawTransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId);

		// Send deployment transaction
		String txHash = txManager.sendTransaction(
				gasPrice,          // gasPrice from your class or fetch dynamically
				estimatedGasLimit,  // use estimated gas limit
				null,              // to = null for contract deployment
				data,
				BigInteger.ZERO    // value = 0 for deployment
		).getTransactionHash();

		log.info("Contract deployment tx hash: {}", txHash);

		// Wait for receipt and get deployed contract address
		TransactionReceipt receipt = waitForTransactionReceipt(txHash);
		String contractAddress = receipt.getContractAddress();

		log.info("Contract deployed at address: {}", contractAddress);
		return contractAddress;
	}

	/**
	 * Estimate gas required for deployment with constructor parameters.
	 *
	 * @param binary Compiled contract bytecode (hex string without 0x)
	 * @param encodedConstructor ABI-encoded constructor parameters hex string
	 * @return estimated gas as BigInteger
	 * @throws Exception if estimation fails
	 */
	public BigInteger estimateGasForDeployment(String binary, String encodedConstructor) throws Exception {
		if (StringUtils.isBlank(binary)) {
			throw new IllegalArgumentException("Contract binary cannot be null or empty");
		}
		if (encodedConstructor == null) {
			encodedConstructor = "";
		}

		String data = "0x" + binary + encodedConstructor;
		String fromAddress = credentials.getAddress();

		// Get nonce for 'from' address from pending to avoid replay issues
		BigInteger nonce = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING)
				.send().getTransactionCount();

		// Fetch current gas price dynamically
		BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(BigInteger.valueOf(2)); //Doubling GAS price to go above minimum threshold

		Transaction transaction = Transaction.createContractTransaction(
				fromAddress,
				nonce,
				gasPrice,
				null,  // gasLimit null for estimation
				BigInteger.ZERO,
				data
		);

		log.info("Estimating gas with from={}, nonce={}, gasPrice={}, data length={}",
				fromAddress, nonce, gasPrice, data.length());

		var response = web3j.ethEstimateGas(transaction).send();

		if (response.hasError()) {
			throw new RuntimeException("Gas estimation failed: " + response.getError().getMessage());
		}

		BigInteger estimatedGas = response.getAmountUsed().multiply(BigInteger.valueOf(2));
		log.info("Estimated gas for contract deployment: {}", estimatedGas);

		return estimatedGas;
	}

	/**
	 * Check if contract is confirmed (code exists at address).
	 *
	 * @param contractAddress contract address hex string starting with 0x
	 * @return true if contract code exists
	 * @throws Exception on web3j failure or invalid input
	 */
	public boolean isContractConfirmed(String contractAddress) throws Exception {
		if (!isValidHexAddress(contractAddress)) {
			throw new IllegalArgumentException("Invalid contract address: " + contractAddress);
		}
		String code = web3j.ethGetCode(contractAddress, DefaultBlockParameterName.LATEST)
				.send()
				.getCode();

		// contract code returned includes "0x" prefix, empty contract code is "0x"
		return code != null && code.length() > 2;
	}

	/**
	 * Fetches the transaction receipt for a given transaction hash.
	 *
	 * @param txHash Transaction hash (hex string with or without 0x)
	 * @return JSON string of the receipt, or null if not mined yet
	 * @throws Exception on web3j call failure or invalid input
	 */
	public String getTransactionReceipt(String txHash) throws Exception {
		String hash = txHash.startsWith("0x") ? txHash : "0x" + txHash;

		if (!isValidHexHash(hash)) {
			throw new IllegalArgumentException("Invalid transaction hash: " + txHash);
		}

		var ethReceiptResponse = web3j.ethGetTransactionReceipt(hash).send();
		var receiptOpt = ethReceiptResponse.getTransactionReceipt();

		if (receiptOpt.isEmpty()) {
			return null; // Transaction not yet mined
		}

		var receipt = receiptOpt.get();

		// Convert receipt object to JSON for client
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writeValueAsString(receipt);
	}

	/**
	 * Validate if string is a valid hex Ethereum address with 0x prefix.
	 *
	 * @param address string to validate
	 * @return true if valid
	 */
	private boolean isValidHexAddress(String address) {
		if (address == null) {
			return false;
		}
		return HEX_PATTERN.matcher(address).matches() && address.length() == 42; // 0x + 40 hex chars
	}

	/**
	 * Validate if string is a valid hex transaction hash with 0x prefix.
	 *
	 * @param hash string to validate
	 * @return true if valid
	 */
	private boolean isValidHexHash(String hash) {
		if (hash == null) {
			return false;
		}
		return HEX_PATTERN.matcher(hash).matches() && hash.length() == 66; // 0x + 64 hex chars
	}

	private TransactionReceipt waitForTransactionReceipt(String transactionHash) throws Exception {
		int attempts = 40;
		int sleepDuration = 1500; // milliseconds

		for (int i = 0; i < attempts; i++) {
			var receiptResponse = web3j.ethGetTransactionReceipt(transactionHash).send();
			var receiptOptional = receiptResponse.getTransactionReceipt();

			if (receiptOptional.isPresent()) {
				return receiptOptional.get();
			}

			Thread.sleep(sleepDuration);
		}

		throw new RuntimeException("Transaction receipt not received after waiting");
	}
}
