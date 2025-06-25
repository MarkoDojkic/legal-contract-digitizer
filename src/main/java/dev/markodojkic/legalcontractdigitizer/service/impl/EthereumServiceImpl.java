package dev.markodojkic.legalcontractdigitizer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.markodojkic.legalcontractdigitizer.dto.ContractPartiesBalanceRequest;
import dev.markodojkic.legalcontractdigitizer.dto.PartyBalanceDto;
import dev.markodojkic.legalcontractdigitizer.enums_records.EthereumContractContext;
import dev.markodojkic.legalcontractdigitizer.exception.*;
import dev.markodojkic.legalcontractdigitizer.service.EthereumService;
import dev.markodojkic.legalcontractdigitizer.util.Web3jUtil;
import jakarta.annotation.PostConstruct;
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
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.AbiDefinition;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EthereumServiceImpl implements EthereumService {

    private static final Pattern HEX_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

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
    public void init() throws EthereumConnectionException {
        try {
            web3j = Web3j.build(new HttpService(ethereumRpcUrl, new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build(), false));
            credentials = Credentials.create(privateKey);
            transactionManager = new RawTransactionManager(web3j, credentials, chainId);

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
            List<Type> abiTypes = Web3jUtil.convertToAbiTypes(constructorParams);
            String encodedConstructor = FunctionEncoder.encodeConstructor(abiTypes);
            return new EthereumContractContext(binary, encodedConstructor);
        } catch (Exception e) {
            log.error("Failed to build deployment context", e);
            throw new InvalidContractBinaryException("Failed to encode constructor parameters: " + e.getMessage());
        }
    }

    @Override
    public String deployCompiledContract(String binary, String encodedConstructor) throws DeploymentFailedException {
        if (binary == null || binary.isBlank()) {
            throw new DeploymentFailedException("Contract binary must not be null or empty", null);
        }
        if (encodedConstructor == null) {
            encodedConstructor = "";
        }

        try {
            String data = "0x" + binary + encodedConstructor;

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger gasLimit = estimateGasForDeployment(binary, encodedConstructor);

            log.info("Deploying contract with gasPrice={} and gasLimit={}", gasPrice, gasLimit);

            String txHash = (transactionManager).sendTransaction(
                    gasPrice,
                    gasLimit,
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
    public BigInteger estimateGasForDeployment(String binary, String encodedConstructor) throws GasEstimationFailedException {
        if (binary == null || binary.isBlank()) {
            throw new GasEstimationFailedException("Contract binary must not be null or empty", null);
        }
        if (encodedConstructor == null) {
            encodedConstructor = "";
        }

        try {
            String data = "0x" + binary + encodedConstructor;
            String from = credentials.getAddress();

            BigInteger nonce = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(BigInteger.valueOf(2)); // safety multiplier

            Transaction tx = Transaction.createContractTransaction(from, nonce, gasPrice, null, BigInteger.ZERO, data);

            var response = web3j.ethEstimateGas(tx).send();

            if (response.hasError()) {
                throw new GasEstimationFailedException("Gas estimation error: " + response.getError().getMessage(), null);
            }

            BigInteger estimatedGas = response.getAmountUsed().multiply(BigInteger.valueOf(2)); // add safety margin

            log.info("Estimated gas for deployment: {} Wei", estimatedGas);

            return estimatedGas;
        } catch (GasEstimationFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gas estimation failed", e);
            throw new GasEstimationFailedException("Gas estimation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isContractConfirmed(String contractAddress) throws InvalidEthereumAddressException, EthereumConnectionException {
        if (!isValidAddress(contractAddress)) {
            throw new InvalidEthereumAddressException("Invalid Ethereum address: " + contractAddress);
        }
        try {
            String code = web3j.ethGetCode(contractAddress, DefaultBlockParameterName.LATEST)
                    .send()
                    .getCode();

            return code != null && !code.equals("0x") && !code.isEmpty();
        } catch (Exception e) {
            log.error("Failed to get contract code", e);
            throw new EthereumConnectionException("Failed to check contract confirmation: " + e.getMessage(), e);
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
            // Convert receipt to JSON string - you can use your favorite JSON lib, here example with Jackson
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(receiptOpt.get());
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
    public BigInteger getBalance(String address) throws InvalidEthereumAddressException, EthereumConnectionException {
        if (!isValidAddress(address)) {
            throw new InvalidEthereumAddressException("Invalid Ethereum address: " + address);
        }

        try {
            return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send()
                    .getBalance();
        } catch (Exception e) {
            log.error("Error retrieving balance for address {}", address, e);
            throw new EthereumConnectionException("Failed to retrieve balance", e);
        }
    }

    @Override
    public String invokeFunction(
            String contractAddress,
            String abiJson,
            String functionName,
            List<Object> params,
            BigInteger valueWei
    ) throws InvalidEthereumAddressException, InvalidFunctionCallException, EthereumConnectionException {
        if (!isValidAddress(contractAddress)) {
            throw new InvalidEthereumAddressException("Invalid Ethereum address: " + contractAddress);
        }

        var abiDef = Web3jUtil.findFunctionDefinition(abiJson, functionName);
        if (abiDef == null) {
            throw new InvalidFunctionCallException("Function '" + functionName + "' not found in ABI");
        }

        try {
            List<Type> inputTypes = Web3jUtil.convertToAbiTypes(params);
            var function = new org.web3j.abi.datatypes.Function(functionName, inputTypes, List.of());

            String data = org.web3j.abi.FunctionEncoder.encode(function);
            BigInteger nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
                    .send()
                    .getTransactionCount();
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger gasLimit = web3j.ethEstimateGas(
                            Transaction.createFunctionCallTransaction(
                                    credentials.getAddress(),
                                    nonce,
                                    gasPrice,
                                    null,
                                    contractAddress,
                                    valueWei == null ? BigInteger.ZERO : valueWei,
                                    data
                            )
                    ).send()
                    .getAmountUsed()
                    .multiply(BigInteger.valueOf(2));

            return transactionManager.sendTransaction(
                    gasPrice,
                    gasLimit,
                    contractAddress,
                    data,
                    valueWei == null ? BigInteger.ZERO : valueWei
            ).getTransactionHash();

        } catch (InvalidFunctionCallException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error invoking " + functionName + " on " + contractAddress, e);
            throw new EthereumConnectionException("Failed to invoke function: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PartyBalanceDto> getContractPartiesBalances(ContractPartiesBalanceRequest request) throws ContractReadException {
        try {
            List<PartyBalanceDto> result = new ArrayList<>();

            // Add contract balance
            BigInteger contractBalanceWei = web3j.ethGetBalance(request.getContractAddress(), DefaultBlockParameterName.LATEST)
                    .send().getBalance();
            BigDecimal contractBalanceEth = Convert.fromWei(new BigDecimal(contractBalanceWei), Convert.Unit.ETHER);
            result.add(PartyBalanceDto.builder()
                    .roleName("contract")
                    .address(request.getContractAddress())
                    .balanceEth(contractBalanceEth)
                    .build());

            // Parse ABI and find all public address-type variables
            List<AbiDefinition> abiDefs = Web3jUtil.parseAbi(request.getAbi());
            for (AbiDefinition def : abiDefs) {
                if ("function".equals(def.getType())
                        && def.getInputs().isEmpty()
                        && def.getOutputs().size() == 1
                        && "address".equals(def.getOutputs().get(0).getType())) {

                    String roleName = def.getName();
                    String address = callAddressGetter(request.getContractAddress(), roleName);
                    if (address != null && !address.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
                        BigInteger balanceWei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
                        BigDecimal balanceEth = Convert.fromWei(new BigDecimal(balanceWei), Convert.Unit.ETHER);
                        result.add(PartyBalanceDto.builder()
                                .roleName(roleName)
                                .address(address)
                                .balanceEth(balanceEth)
                                .build());
                    }
                }
            }

            return result;
        } catch (Exception e) {
            throw new ContractReadException("Failed to read contract parties and balances", e);
        }
    }

    private String callAddressGetter(String contractAddress, String getterName) throws Exception {
        Function function = new Function(
                getterName,
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Address>() {})
        );
        String encoded = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, contractAddress, encoded),
                DefaultBlockParameterName.LATEST
        ).send();

        if (response.isReverted() || response.hasError()) return null;

        List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (decoded.isEmpty()) return null;

        return ((Address) decoded.get(0)).getValue();
    }


}