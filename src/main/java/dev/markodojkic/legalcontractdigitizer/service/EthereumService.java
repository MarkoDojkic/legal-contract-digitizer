package dev.markodojkic.legalcontractdigitizer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.crypto.Credentials;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.List;

@Service
@Slf4j
public class EthereumService {

	private Web3j web3j;
	private TransactionManager transactionManager;
	private ContractGasProvider gasProvider;
	private Credentials credentials;

	@Value("${ethereum.rpc.url}")
	private String ethereumRpcUrl;

	@Value("${ethereum.private.key}")
	private String privateKey;

	@Value("${ethereum.chain.id:1337}")
	private long chainId;

	@Value("${ethereum.gas.price:20000000000}") // 20 Gwei default
	private BigInteger gasPrice;

	@Value("${ethereum.gas.limit:4500000}") // 4.5 million default
	private BigInteger gasLimit;

	@PostConstruct
	public void init() {
		try {
			web3j = Web3j.build(new HttpService(ethereumRpcUrl));
			credentials = Credentials.create(privateKey);
			transactionManager = new RawTransactionManager(web3j, credentials, chainId);
			gasProvider = new FixedGasProvider(gasPrice, gasLimit);

			log.info("EthereumService initialized with RPC: {}, ChainId: {}", ethereumRpcUrl, chainId);
		} catch (Exception e) {
			log.error("EthereumService initialization failed", e);
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Deploy contract with constructor parameters.
	 *
	 * @param binary Compiled contract bytecode (hex string without 0x)
	 * @param constructorParameters List of ABI Types matching the Solidity constructor signature
	 * @return deployed contract address
	 * @throws Exception if deployment fails
	 */
	@Retryable(
			retryFor = { Exception.class },
			maxAttempts = 3,
			backoff = @Backoff(delay = 2000, multiplier = 2))
	public String deployContractWithConstructor(String binary, List<Type> constructorParameters) throws Exception {
		String encodedConstructor = FunctionEncoder.encodeConstructor(constructorParameters);
		log.info("Encoded constructor parameters: {}", encodedConstructor);
		return deployCompiledContract(binary, encodedConstructor);
	}

	/**
	 * Deploy contract with encoded constructor data.
	 *
	 * @param binary Compiled contract bytecode (hex string without 0x)
	 * @param encodedConstructor ABI-encoded constructor parameters hex string
	 * @return deployed contract address
	 * @throws Exception if deployment fails
	 */
	@Retryable(
			retryFor = { Exception.class },
			maxAttempts = 3,
			backoff = @Backoff(delay = 2000, multiplier = 2))
	public String deployCompiledContract(String binary, String encodedConstructor) throws Exception {
		log.info("Deploying contract, binary length: {}, constructor data length: {}", binary.length(), encodedConstructor.length());

		RemoteCall<Contract> deployCall = Contract.deployRemoteCall(
				Contract.class,
				web3j,
				transactionManager,
				gasProvider,
				binary,
				encodedConstructor,
				BigInteger.ZERO
		);

		Contract contract = deployCall.send();

		String deployedAddress = contract.getContractAddress();
		log.info("Contract deployed at address: {}", deployedAddress);
		return deployedAddress;
	}

	public boolean isContractConfirmed(String contractAddress) throws Exception {
		return web3j.ethGetCode(contractAddress, DefaultBlockParameterName.LATEST)
				.send()
				.getCode()
				.length() > 10;
	}

	/**
	 * Fetches the transaction receipt for a given transaction hash.
	 *
	 * @param txHash Transaction hash (hex string with or without 0x)
	 * @return JSON string of the receipt, or null if not mined yet
	 * @throws Exception on web3j call failure
	 */
	public String getTransactionReceipt(String txHash) throws Exception {
		String hash = txHash.startsWith("0x") ? txHash : "0x" + txHash;

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

	private static class FixedGasProvider implements ContractGasProvider {
		private final BigInteger gasPrice;
		private final BigInteger gasLimit;

		public FixedGasProvider(BigInteger gasPrice, BigInteger gasLimit) {
			this.gasPrice = gasPrice;
			this.gasLimit = gasLimit;
		}

		@Override
		public BigInteger getGasPrice(String contractFunc) {
			return gasPrice;
		}

		@Override
		public BigInteger getGasPrice() {
			return gasPrice;
		}

		@Override
		public BigInteger getGasLimit(String contractFunc) {
			return gasLimit;
		}

		@Override
		public BigInteger getGasLimit() {
			return gasLimit;
		}
	}
}