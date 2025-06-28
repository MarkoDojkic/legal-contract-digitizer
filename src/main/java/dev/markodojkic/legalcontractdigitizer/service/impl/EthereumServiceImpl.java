package dev.markodojkic.legalcontractdigitizer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.markodojkic.legalcontractdigitizer.enums_records.EthereumContractContext;
import dev.markodojkic.legalcontractdigitizer.exception.*;
import dev.markodojkic.legalcontractdigitizer.service.IEthereumService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
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
import org.web3j.protocol.core.methods.response.AbiDefinition;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
    public void init() throws EthereumConnectionException {
        try {
            web3j = Web3j.build(new HttpService(ethereumRpcUrl, new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build(), false));

            log.info("EthereumService initialized with RPC {}, chainId {}", ethereumRpcUrl, chainId);
        } catch (Exception e) {
            log.error("Failed to initialize EthereumService", e);
            throw new EthereumConnectionException("Failed to connect to Ethereum RPC", e);
        }
    }

    @Override
    public EthereumContractContext buildDeploymentContext(String binary, List<Object> constructorParams) throws InvalidContractBinaryException {
        if (binary == null || binary.isBlank()) {
            throw new InvalidContractBinaryException("Contract binary must not be null or empty");
        }
        try {
            List<Type> abiTypes = convertToAbiTypes(constructorParams);
            String encodedConstructor = FunctionEncoder.encodeConstructor(abiTypes);
            return new EthereumContractContext(binary, encodedConstructor.replaceFirst("^0x", ""));
        } catch (Exception e) {
            log.error("Failed to build deployment context", e);
            throw new InvalidContractBinaryException("Failed to encode constructor parameters: " + e.getMessage());
        }
    }

    @Override
    public String deployCompiledContract(String binary, String encodedConstructor, Credentials credentials) throws DeploymentFailedException {
        if (binary == null || binary.isBlank()) {
            throw new DeploymentFailedException("Contract binary must not be null or empty", null);
        }
        if (encodedConstructor == null) {
            throw new DeploymentFailedException("Encoded constructor must not be null or empty", null);
        }

        try {
            String data = "0x" + binary + encodedConstructor;

            List<BigInteger> gas = estimateGasForDeployment(binary, encodedConstructor, credentials.getAddress());

            log.info("Deploying contract with gasLimit={} units and gasPrice={} wei", gas.getFirst(), gas.get(1));

            String txHash = new RawTransactionManager(web3j, credentials, chainId).sendTransaction(
                    gas.get(1),
                    gas.getFirst(),
                    null,
                    data,
                    BigInteger.ZERO
            ).getTransactionHash();

            log.info("Deployment transaction sent with hash {}", txHash);

            TransactionReceipt receipt = waitForTransactionReceipt(txHash);

            String contractAddress = receipt.getContractAddress();
            log.info("Contract deployed at address {}", contractAddress);

            return contractAddress;
        } catch (Exception e) {
            log.error("Contract deployment failed", e);
            throw new DeploymentFailedException("Contract deployment failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<BigInteger> estimateGasForDeployment(String binary, String encodedConstructor, String deployerWalletAddress) throws GasEstimationFailedException {
        if (binary == null || binary.isBlank()) {
            throw new GasEstimationFailedException("Contract binary must not be null or empty", null);
        }
        if (encodedConstructor == null) {
            throw new GasEstimationFailedException("Encoded constructor must not be null or empty", null);
        }

        try {
            String data = "0x" + binary + encodedConstructor;

            BigInteger nonce = web3j.ethGetTransactionCount(deployerWalletAddress, DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();


            // Use extracted gas estimation method
            List<BigInteger> gasEstimation = estimateGas(
                    deployerWalletAddress,
                    nonce,
                    null, // No contract address for deployment
                    BigInteger.ZERO, // No value sent
                    data
            );

            log.info("Estimating gas for deployment with nonce={}, gasPrice={} wei", nonce, gasEstimation.get(1));

            log.info("Estimated gas limit for deployment: {} units", gasEstimation.get(0));

            return List.of(
                    gasEstimation.get(0), // Gas limit with safety margin
                    gasEstimation.get(1) // Current gas price
            );
        } catch (GasEstimationFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gas estimation failed", e);
            throw new GasEstimationFailedException("Gas estimation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean doesSmartContractExist(String contractAddress)
            throws InvalidEthereumAddressException, EthereumConnectionException {
        if (!isValidAddress(contractAddress)) {
            throw new InvalidEthereumAddressException("Invalid Ethereum address: " + contractAddress);
        }

        try {
            // Step 1: Check code existence
            String code = web3j.ethGetCode(contractAddress, DefaultBlockParameterName.LATEST)
                    .send()
                    .getCode();

            if (code == null || code.equals("0x") || code.equals("0x0")) return false; // No code deployed at address

            // Step 2: Check `destroyed` flag via eth_call

            // Prepare function call data for `destroyed() public view returns (bool)`
            String methodId = "0x359cbbc9";

            Transaction callTransaction = Transaction.createEthCallTransaction(
                    null, // from address can be null for eth_call
                    contractAddress,
                    methodId
            );

            EthCall response = web3j.ethCall(callTransaction, DefaultBlockParameterName.LATEST).send();

            return response.isReverted();

        } catch (Exception e) {
            log.error("Failed to get contract code or destroyed flag", e);
            throw new EthereumConnectionException("Failed to check contract existence: " + e.getMessage(), e);
        }
    }


    @Override
    public String getTransactionReceipt(String txHash) throws IllegalArgumentException, EthereumConnectionException {
        if (txHash == null || txHash.isBlank()) {
            throw new IllegalArgumentException("Transaction hash must not be empty");
        }
        try {
            var receiptOpt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (receiptOpt.isEmpty()) {
                return null; // Receipt not yet available
            }

            return objectMapper.writeValueAsString(receiptOpt.get());
        } catch (IOException e) {
            log.error("Failed to serialize transaction receipt", e);
            throw new EthereumConnectionException("Failed to process transaction receipt JSON", e);
        } catch (Exception e) {
            log.error("Failed to get transaction receipt", e);
            throw new EthereumConnectionException("Failed to get transaction receipt: " + e.getMessage(), e);
        }
    }

    private boolean isValidAddress(String address) {
        return address != null && HEX_ADDRESS_PATTERN.matcher(address).matches();
    }

    // Waits for transaction receipt — implement with timeout or polling as you see fit
    private TransactionReceipt waitForTransactionReceipt(String txHash) throws DeploymentFailedException {
        try {
            int attempts = 40;
            int sleepDuration = 1500; // ms
            for (int i = 0; i < attempts; i++) {
                var receiptOptional = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
                if (receiptOptional.isPresent()) {
                    return receiptOptional.get();
                }
                Thread.sleep(sleepDuration);
            }
            throw new DeploymentFailedException("Transaction receipt not found for txHash: " + txHash, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeploymentFailedException("Thread interrupted while waiting for transaction receipt", e);
        } catch (Exception e) {
            throw new DeploymentFailedException("Failed to get transaction receipt: " + e.getMessage(), e);
        }
    }

    @Override
    public BigDecimal getBalance(String address) throws InvalidEthereumAddressException, EthereumConnectionException {
        if (!isValidAddress(address)) {
            throw new InvalidEthereumAddressException("Invalid Ethereum address: " + address);
        }

        try {
            return Convert.fromWei(new BigDecimal(web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send()
                    .getBalance()), Convert.Unit.ETHER);
        } catch (Exception e) {
            log.error("Error retrieving balance for address {}", address, e);
            throw new EthereumConnectionException("Failed to retrieve balance", e);
        }
    }



    @Override
    public String invokeFunction(
            String contractAddress,
            String functionName,
            List<Object> params,
            BigInteger valueWei,
            Credentials credentials) throws InvalidEthereumAddressException, InvalidFunctionCallException, EthereumConnectionException {

        if (!isValidAddress(contractAddress)) {
            throw new InvalidEthereumAddressException("Invalid Ethereum address: " + contractAddress);
        }

        try {
            List<Type> inputTypes = convertToAbiTypes(params);
            var function = new org.web3j.abi.datatypes.Function(functionName, inputTypes, List.of());

            String data = org.web3j.abi.FunctionEncoder.encode(function);
            BigInteger nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
                    .send()
                    .getTransactionCount();

            List<BigInteger> gasEstimation = estimateGas(
                    credentials.getAddress(),
                    nonce,
                    contractAddress,
                    valueWei,
                    data
            );

            return new RawTransactionManager(web3j, credentials, chainId).sendTransaction(
                    gasEstimation.get(1), // Gas price
                    gasEstimation.get(0), // Gas limit with safety margin
                    contractAddress,
                    data,
                    valueWei
            ).getTransactionHash();

        } catch (InvalidFunctionCallException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error invoking " + functionName + " on " + contractAddress, e);
            throw new EthereumConnectionException("Failed to invoke function: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> resolveAddressGetters(String contractAddress, List<String> getterFunctions) {
        Map<String, String> results = new HashMap<>();
        for (String getter : getterFunctions) {
            try {
                Function function = new Function(getter, List.of(), List.of(new TypeReference<Address>() {}));
                String encodedFunction = FunctionEncoder.encode(function);

                EthCall response = web3j.ethCall(
                        Transaction.createEthCallTransaction(
                                "0x10F5d45854e038071485AC9e402308cF80D2d2fE",
                                contractAddress,
                                encodedFunction),
                        DefaultBlockParameterName.LATEST).send();

                List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
                if (!decoded.isEmpty()) {
                    results.put(getter, decoded.get(0).getValue().toString());
                }
            } catch (Exception e) {
                results.put(getter, "ERROR");
                log.warn("Error calling getter " + getter, e);
            }
        }
        return results;
    }


    private List<Type> convertToAbiTypes(List<Object> constructorParams) {
        List<Type> abiTypes = new ArrayList<>();

        for (Object param : constructorParams) {
            switch (param) {
                case String strParam -> {
                    if (strParam.matches("^0x[a-fA-F0-9]{40}$")) {
                        // Ethereum address
                        abiTypes.add(new Address(strParam));
                    } else if (strParam.matches("^\\d+$")) {
                        // Numeric string
                        abiTypes.add(new Uint256(new BigInteger(strParam)));
                    } else {
                        // Fallback to string
                        abiTypes.add(new Utf8String(strParam));
                    }
                }
                case Number num -> abiTypes.add(new Uint256(BigInteger.valueOf(num.longValue())));
                default -> throw new IllegalArgumentException("Unsupported constructor parameter type: " + param);
            }
        }

        return abiTypes;
    }

    /**
     * Parses ABI JSON and returns list of all definitions.
     */
    private List<AbiDefinition> parseAbi(String abiJson) {
        try {
            return objectMapper.readValue(abiJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            throw new InvalidFunctionCallException("Failed to parse ABI: " + e.getMessage(), e);
        }
    }

    /**
     * Estimates gas limit for a transaction.
     *
     * @param from          the sender address
     * @param nonce         transaction nonce
     * @param to            contract address or null for deployment
     * @param valueWei      amount of wei to send
     * @param data          encoded function or contract deployment data
     * @return estimated gas limit with safety margin
     */
    private List<BigInteger> estimateGas(
            String from,
            BigInteger nonce,
            String to,
            BigInteger valueWei,
            String data) throws GasEstimationFailedException {

        try {
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            Transaction tx;
            if (to == null || to.isBlank()) {
                // Deployment transaction
                tx = Transaction.createContractTransaction(from, nonce, gasPrice, null, valueWei, data);
            } else {
                // Function call transaction
                tx = Transaction.createFunctionCallTransaction(from, nonce, gasPrice, null, to, valueWei, data);
            }

            var response = web3j.ethEstimateGas(tx).send();

            if (response.hasError()) {
                throw new GasEstimationFailedException("Gas estimation error: " + response.getError().getMessage(), null);
            }

            BigInteger gasUsed = response.getAmountUsed();

            if (gasUsed == null || gasUsed.equals(BigInteger.ZERO)) {
                throw new GasEstimationFailedException("Gas estimation returned zero gas", null);
            }

            // Multiply gas by 2 as a safety margin
            return List.of(
                    gasUsed.multiply(BigInteger.valueOf(2)), // Gas limit with safety margin
                    gasPrice // Current gas price
            );
        } catch (IOException e) {
            throw new GasEstimationFailedException("IOException during gas estimation: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new GasEstimationFailedException("Gas estimation failed: " + e.getMessage(), e);
        }
    }
}