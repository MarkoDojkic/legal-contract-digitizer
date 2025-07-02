package dev.markodojkic.legalcontractdigitizer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.markodojkic.legalcontractdigitizer.model.EthereumContractContext;
import dev.markodojkic.legalcontractdigitizer.exception.*;
import dev.markodojkic.legalcontractdigitizer.service.IEthereumService;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class EthereumServiceImpl implements IEthereumService {

    private static final Pattern HEX_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final ObjectMapper objectMapper;
    private Web3j web3j;

    @Value("${ethereum.rpc.url}")
    private String ethereumRpcUrl;

    @Value("${ethereum.chain.id:1337}")
    private long chainId;

    @PostConstruct
    public void init() {
        try {
            web3j = Web3j.build(new HttpService(ethereumRpcUrl, HttpClientUtil.client, false));

            log.debug("EthereumService initialized with RPC {}, chainId {}", ethereumRpcUrl, chainId);
        } catch (Exception e) {
            log.error("Failed to initialize EthereumService", e);
            throw new RuntimeException("Failed to connect to Ethereum RPC", e);
        }
    }

    @Override
    public EthereumContractContext buildDeploymentContext(String binary, List<Object> constructorParams) throws InvalidContractBinaryException {
        if (binary == null || binary.isBlank()) throw new InvalidContractBinaryException("Contract binary must not be null or empty");
        try {
            List<Type> abiTypes = convertToAbiTypes(constructorParams);
            String encodedConstructor = FunctionEncoder.encodeConstructor(abiTypes);
            return new EthereumContractContext(binary, encodedConstructor.replaceFirst("^0x", ""));
        } catch (Exception e) {
            log.error("Failed to build deployment context", e);
            throw new InvalidContractBinaryException("Failed to encode constructor parameters: " + e.getLocalizedMessage());
        }
    }

    @Override
    public String deployCompiledContract(String binary, String encodedConstructor, Credentials credentials) throws DeploymentFailedException {
        if (binary == null || binary.isBlank()) throw new DeploymentFailedException("Contract binary must not be null or empty", null);
        if (encodedConstructor == null) throw new DeploymentFailedException("Encoded constructor must not be null or empty", null);

        try {
            Pair<BigInteger, BigInteger> estimateGasForDeployment = estimateGasForDeployment(binary, encodedConstructor, credentials.getAddress());

            String txHash = new RawTransactionManager(web3j, credentials, chainId).sendTransaction(
                    estimateGasForDeployment.getLeft(),
                    estimateGasForDeployment.getRight(),
                    null,
                    "0x" + binary + encodedConstructor,
                    BigInteger.ZERO
            ).getTransactionHash();

            log.debug("Deployment transaction sent with hash {}", txHash);

            String contractAddress = waitForTransactionReceipt(txHash).getContractAddress();
            log.debug("Contract deployed at address {}", contractAddress);

            return contractAddress;
        } catch (Exception e) {
            log.error("Contract deployment failed", e);
            throw new DeploymentFailedException("Contract deployment failed: " + e.getLocalizedMessage(), e);
        }
    }

    @Override
    public Pair<BigInteger, BigInteger> estimateGasForDeployment(String binary, String encodedConstructor, String deployerWalletAddress) throws GasEstimationFailedException, InvalidContractBinaryException {
        if (binary == null || binary.isBlank()) throw new InvalidContractBinaryException("Contract binary must not be null or empty");

        if (encodedConstructor == null) throw new GasEstimationFailedException("Encoded constructor must not be null or empty");

        try {
            BigInteger nonce = web3j.ethGetTransactionCount(deployerWalletAddress, DefaultBlockParameterName.PENDING).send().getTransactionCount();

            Pair<BigInteger, BigInteger> gasEstimation = estimateGas(deployerWalletAddress, nonce,
                    null, // No contract address for deployment
                    BigInteger.ZERO, // No ethereum needed for deployment
                    "0x" + binary + encodedConstructor);

            log.debug("Estimated gas with nonce \"{}\": {} units (≈ {} Sepolia ETH @ {} Gwei)",
                    nonce,
                    String.format("%,d", gasEstimation.getRight()),
                    Convert.fromWei(new BigDecimal(gasEstimation.getRight().multiply(gasEstimation.getLeft())), Convert.Unit.ETHER).setScale(6, RoundingMode.HALF_UP).toPlainString(),
                    Convert.fromWei(new BigDecimal(gasEstimation.getLeft()), Convert.Unit.GWEI).setScale(6, RoundingMode.HALF_UP).toPlainString());

            return Pair.of(gasEstimation.getLeft(), gasEstimation.getRight());
        } catch (Exception e) {
            log.error("Gas estimation failed", e);
            throw new GasEstimationFailedException(e.getLocalizedMessage());
        }
    }

    @Override
    public boolean doesSmartContractExist(String contractAddress)
            throws InvalidEthereumAddressException, EthereumConnectionException {
        if (contractAddress == null || !HEX_ADDRESS_PATTERN.matcher(contractAddress).matches()) throw new InvalidEthereumAddressException("Invalid Ethereum address: " + contractAddress);

        try {
            // Step 1: Check code existence
            String code = web3j.ethGetCode(contractAddress, DefaultBlockParameterName.LATEST)
                    .send()
                    .getCode();

            if (code == null || code.equals("0x") || code.equals("0x0")) return false; // No code deployed at address

            // Step 2: Check `destroyed` flag via eth_call

            return web3j.ethCall(Transaction.createEthCallTransaction(
                    null, // from address can be null for eth_call
                    contractAddress,
                    "0x359cbbc9"), DefaultBlockParameterName.LATEST).send().isReverted();

        } catch (Exception e) {
            log.error("Failed to get contract code or destroyed flag", e);
            throw new EthereumConnectionException("Failed to check contract existence: " + e.getLocalizedMessage());
        }
    }

    @Override
    public String getTransactionReceipt(String txHash) throws IllegalArgumentException, EthereumConnectionException {
        if (txHash == null || txHash.isBlank()) throw new IllegalArgumentException("Transaction hash must not be empty");

        try {
            var receiptOpt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (receiptOpt.isEmpty()) return null; // Receipt not yet available

            return objectMapper.writeValueAsString(receiptOpt.get());
        } catch (IOException e) {
            log.error("Failed to serialize transaction receipt", e);
            throw new EthereumConnectionException("Failed to process transaction receipt JSON: " + e.getLocalizedMessage());
        } catch (Exception e) {
            log.error("Failed to get transaction receipt", e);
            throw new EthereumConnectionException("Failed to get transaction receipt: " + e.getLocalizedMessage());
        }
    }

    @Override
    public BigDecimal getBalance(String contractAddress) throws InvalidEthereumAddressException, EthereumConnectionException {
        if (contractAddress == null || !HEX_ADDRESS_PATTERN.matcher(contractAddress).matches()) throw new InvalidEthereumAddressException("Invalid Ethereum address: " + contractAddress);

        try {
            return Convert.fromWei(new BigDecimal(web3j.ethGetBalance(contractAddress, DefaultBlockParameterName.LATEST).send().getBalance()), Convert.Unit.ETHER);
        } catch (Exception e) {
            log.error("Error retrieving balance for address {}", contractAddress, e);
            throw new EthereumConnectionException(e.getLocalizedMessage());
        }
    }

    @Override
    public String invokeFunction(String contractAddress, String functionName, List<Object> params, BigInteger valueWei, Credentials credentials) throws InvalidEthereumAddressException, InvalidFunctionCallException, GasEstimationFailedException {
        if (contractAddress == null || !HEX_ADDRESS_PATTERN.matcher(contractAddress).matches()) throw new InvalidEthereumAddressException("Invalid Ethereum address: " + contractAddress);

        try {
            String data = org.web3j.abi.FunctionEncoder.encode(new org.web3j.abi.datatypes.Function(functionName, convertToAbiTypes(params), List.of()));

            Pair<BigInteger, BigInteger> gasEstimation = estimateGas(credentials.getAddress(), web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount(), contractAddress, valueWei, data);

            return new RawTransactionManager(web3j, credentials, chainId).sendTransaction(gasEstimation.getLeft(), gasEstimation.getRight(), contractAddress, data, valueWei).getTransactionHash();
        } catch (GasEstimationFailedException e){
            throw e;
        } catch (Exception e) {
	        log.error("Error invoking {} on {}", functionName, contractAddress, e);
            throw new InvalidFunctionCallException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public Map<String, String> resolveContractPartiesAddressData(String contractAddress, List<String> getterFunctions) {
        Map<String, String> results = new HashMap<>();
        for (String getter : getterFunctions) {
            try {
                Function function = new Function(getter, List.of(), List.of(new TypeReference<Address>() {}));

                List<Type> decoded = FunctionReturnDecoder.decode(web3j.ethCall(
                        Transaction.createEthCallTransaction(
                                null, // from address can be null for eth_call
                                contractAddress,
                                FunctionEncoder.encode(function)),
                        DefaultBlockParameterName.LATEST).send().getValue(), function.getOutputParameters());
                if (!decoded.isEmpty()) results.put(getter, decoded.getFirst().getValue().toString());
            } catch (Exception e) {
                results.put(getter, e.getLocalizedMessage());
	            log.error("Error calling getter {}", getter, e);
            }
        }
        return results;
    }

    private TransactionReceipt waitForTransactionReceipt(String txHash) throws DeploymentFailedException {
        try {
            int attempts = 40;
            int sleepDuration = 1500; // ms
            for (int i = 0; i < attempts; i++) {
                var receiptOptional = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
                if (receiptOptional.isPresent()) return receiptOptional.get();
                Thread.sleep(sleepDuration);
            }
            throw new DeploymentFailedException("Transaction receipt not found for txHash: " + txHash, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeploymentFailedException("Thread interrupted while waiting for transaction receipt", e);
        } catch (Exception e) {
            throw new DeploymentFailedException("Failed to get transaction receipt: " + e.getLocalizedMessage(), e);
        }
    }

    private List<Type> convertToAbiTypes(List<Object> constructorParams) {
        List<Type> abiTypes = new ArrayList<>();

        for (Object param : constructorParams) {
            switch (param) {
                case String strParam -> {
                    if (strParam.matches("^0x[a-fA-F0-9]{40}$")) abiTypes.add(new Address(strParam)); // Ethereum address
                    else if (strParam.matches("^\\d+$")) abiTypes.add(new Uint256(new BigInteger(strParam))); // Numeric string
                    else abiTypes.add(new Utf8String(strParam)); // Fallback to string
                }
                case Number num -> abiTypes.add(new Uint256(BigInteger.valueOf(num.longValue())));
                default -> throw new IllegalArgumentException("Unsupported constructor parameter type: " + param);
            }
        }

        return abiTypes;
    }

    /**
     * Estimates gas limit for a transaction.
     *
     * @param from          the sender address
     * @param nonce         transaction nonce
     * @param to            contract address or null for deployment
     * @param valueWei      amount of wei to send
     * @param data          encoded function or contract deployment data
     * @return Pair of current gas price and estimated gas limit with safety margin
     */
    private Pair<BigInteger, BigInteger> estimateGas(String from, BigInteger nonce, String to, BigInteger valueWei, String data) throws GasEstimationFailedException {
        try {
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            Transaction tx;
            if (to == null || to.isBlank()) tx = Transaction.createContractTransaction(from, nonce, gasPrice, null, valueWei, data); // Deployment transaction
            else tx = Transaction.createFunctionCallTransaction(from, nonce, gasPrice, null, to, valueWei, data); // Function call transaction

            var response = web3j.ethEstimateGas(tx).send();

            if (response.hasError()) throw new GasEstimationFailedException(response.getError().getMessage());

            BigInteger gasUsed = response.getAmountUsed();

            if (gasUsed == null || gasUsed.equals(BigInteger.ZERO)) throw new GasEstimationFailedException("Gas estimation returned zero gas");

            // Multiply gas by 2 as a safety margin
            return Pair.of(gasPrice, gasUsed.multiply(BigInteger.valueOf(2)));
        } catch (Exception e) {
            throw new GasEstimationFailedException(e.getLocalizedMessage());
        }
    }
}