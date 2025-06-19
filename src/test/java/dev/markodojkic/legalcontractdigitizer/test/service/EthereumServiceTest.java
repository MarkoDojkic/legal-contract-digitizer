package dev.markodojkic.legalcontractdigitizer.test.service;

import dev.markodojkic.legalcontractdigitizer.service.EthereumService;
import dev.markodojkic.legalcontractdigitizer.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EthereumServiceTest {

    @InjectMocks
    private EthereumService ethereumService;

    @Mock
    private Web3j web3j;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private ContractGasProvider gasProvider;

    @Mock
    private Credentials credentials;

    @BeforeEach
    void setUp() {
        // Inject mocks via reflection since fields are private and set in @PostConstruct
        TestUtils.setField(ethereumService, "web3j", web3j);
        TestUtils.setField(ethereumService, "transactionManager", transactionManager);
        TestUtils.setField(ethereumService, "gasProvider", gasProvider);
        TestUtils.setField(ethereumService, "credentials", credentials);
    }

    @Test
    void deployContractWithConstructorReturnsAddressOnSuccess() throws Exception {
        String binary = "6060604052";
        List<Type> params = List.of();
        RemoteCall<Contract> deployCall = mock(RemoteCall.class);
        Contract contract = mock(Contract.class);

        try (MockedStatic<Contract> contractMockedStatic = mockStatic(Contract.class)) {
            contractMockedStatic.when(() -> Contract.deployRemoteCall(
                    eq(Contract.class),
                    eq(web3j),
                    eq(transactionManager),
                    eq(gasProvider),
                    eq(binary),
                    anyString(),
                    eq(BigInteger.ZERO)
            )).thenReturn(deployCall);

            when(deployCall.send()).thenReturn(contract);
            when(contract.getContractAddress()).thenReturn("0xabc123");

            String address = ethereumService.deployContractWithConstructor(binary, params);
            assertEquals("0xabc123", address);
        }
    }

    @Test
    void deployCompiledContractThrowsExceptionOnFailure() throws Exception {
        String binary = "6060604052";
        String encodedConstructor = "0x1234";
        RemoteCall<Contract> deployCall = mock(RemoteCall.class);

        try (MockedStatic<Contract> contractMockedStatic = mockStatic(Contract.class)) {
            contractMockedStatic.when(() -> Contract.deployRemoteCall(
                    eq(Contract.class),
                    eq(web3j),
                    eq(transactionManager),
                    eq(gasProvider),
                    eq(binary),
                    eq(encodedConstructor),
                    eq(BigInteger.ZERO)
            )).thenReturn(deployCall);

            when(deployCall.send()).thenThrow(new RuntimeException("Deployment failed"));

            Exception ex = assertThrows(Exception.class, () -> ethereumService.deployCompiledContract(binary, encodedConstructor));
            assertTrue(ex.getMessage().contains("Deployment failed"));
        }
    }

    @Test
    void isContractConfirmedReturnsTrueForValidCode() throws Exception {
        EthGetCode ethGetCode = mock(EthGetCode.class);
        var request = mock(Request.class);
        when(web3j.ethGetCode(anyString(), eq(DefaultBlockParameterName.LATEST))).thenReturn(request);
        when(request.send()).thenReturn(ethGetCode);
        when(ethGetCode.getCode()).thenReturn("0x1234567890abc");

        boolean confirmed = ethereumService.isContractConfirmed("0xabc123");
        assertTrue(confirmed);
    }

    @Test
    void isContractConfirmedReturnsFalseForShortCode() throws Exception {
        EthGetCode ethGetCode = mock(EthGetCode.class);
        var request = mock(Request.class);
        when(web3j.ethGetCode(anyString(), eq(DefaultBlockParameterName.LATEST))).thenReturn(request);
        when(request.send()).thenReturn(ethGetCode);
        when(ethGetCode.getCode()).thenReturn("0x123");

        boolean confirmed = ethereumService.isContractConfirmed("0xabc123");
        assertFalse(confirmed);
    }

    @Test
    void getTransactionReceiptReturnsJsonOnReceiptPresent() throws Exception {
        EthGetTransactionReceipt ethGetTransactionReceipt = mock(EthGetTransactionReceipt.class);
        var request = mock(Request.class);
        when(web3j.ethGetTransactionReceipt(anyString())).thenReturn(request);
        when(request.send()).thenReturn(ethGetTransactionReceipt);
        when(ethGetTransactionReceipt.getTransactionReceipt()).thenReturn(Optional.of(mock(TransactionReceipt.class)));

        String json = ethereumService.getTransactionReceipt("0xabc");
        assertNotNull(json);
        assertTrue(json.contains("{"));
    }

    @Test
    void getTransactionReceiptReturnsNullWhenReceiptNotPresent() throws Exception {
        EthGetTransactionReceipt ethGetTransactionReceipt = mock(EthGetTransactionReceipt.class);
        var request = mock(Request.class);
        when(web3j.ethGetTransactionReceipt(anyString())).thenReturn(request);
        when(request.send()).thenReturn(ethGetTransactionReceipt);
        when(ethGetTransactionReceipt.getTransactionReceipt()).thenReturn(Optional.empty());

        String json = ethereumService.getTransactionReceipt("0xabc");
        assertNull(json);
    }

    @Test
    void deployContractWithConstructorThrowsExceptionOnInvalifirestoreinary() {
        String invalifirestoreinary = "";
        List<Type> params = List.of();

        Exception ex = assertThrows(Exception.class, () -> ethereumService.deployContractWithConstructor(invalifirestoreinary, params));
        assertNotNull(ex.getMessage());
    }

    @Test
    void deployCompiledContractThrowsExceptionOnNullEncodedConstructor() {
        String binary = "6060604052";
        String encodedConstructor = null;

        Exception ex = assertThrows(Exception.class, () -> ethereumService.deployCompiledContract(binary, encodedConstructor));
        assertNotNull(ex.getMessage());
    }

    @Test
    void isContractConfirmedThrowsExceptionOnNullAddress() {
        Exception ex = assertThrows(Exception.class, () -> ethereumService.isContractConfirmed(null));
        assertNotNull(ex.getMessage());
    }

    @Test
    void getTransactionReceiptThrowsExceptionOnNullHash() {
        Exception ex = assertThrows(Exception.class, () -> ethereumService.getTransactionReceipt(null));
        assertNotNull(ex.getMessage());
    }
}