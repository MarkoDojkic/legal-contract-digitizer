package dev.markodojkic.legalcontractdigitizer.test.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import dev.markodojkic.legalcontractdigitizer.dto.CompilationResultDTO;
import dev.markodojkic.legalcontractdigitizer.dto.DeploymentStatusResponseDTO;
import dev.markodojkic.legalcontractdigitizer.test.TestUtils;
import dev.markodojkic.legalcontractdigitizer.util.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.enums.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.service.AIService;
import dev.markodojkic.legalcontractdigitizer.service.ContractServiceImpl;
import dev.markodojkic.legalcontractdigitizer.service.EthereumService;
import dev.markodojkic.legalcontractdigitizer.service.FirebaseAuthService;
import dev.markodojkic.legalcontractdigitizer.util.SolidityCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {

    @InjectMocks
    private ContractServiceImpl contractService;

    @Mock
    private FirebaseAuthService firebaseAuthService;
    @Mock
    private AIService aiService;
    @Mock
    private EthereumService ethereumService;
    @Mock
    private SolidityCompiler solidityCompiler;

    @Mock
    private Firestore firestore;
    @Mock
    private CollectionReference collectionReference;
    @Mock
    private DocumentReference documentReference;
    @Mock
    private DocumentSnapshot documentSnapshot;
    @Mock
    private ApiFuture<DocumentSnapshot> apiFuture;
    @Mock
    private ApiFuture<WriteResult> writeResultApiFuture;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        Mockito.reset(firebaseAuthService, aiService, ethereumService, solidityCompiler,
                firestore, collectionReference, documentReference, documentSnapshot, apiFuture, writeResultApiFuture);
        MockitoAnnotations.openMocks(this);

        // Firestore mocking
        lenient().when(documentSnapshot.exists()).thenReturn(true);
        when(firestore.collection("contracts")).thenReturn(collectionReference);
        when(collectionReference.document(anyString())).thenReturn(documentReference);
        lenient().when(documentReference.get()).thenReturn(apiFuture);
        lenient().when(apiFuture.get()).thenReturn(documentSnapshot);
        lenient().when(documentReference.update(anyMap())).thenReturn(writeResultApiFuture);
        lenient().when(writeResultApiFuture.get()).thenReturn(mock(WriteResult.class));

        // Inject mocks into the service
        TestUtils.setField(contractService, "firebaseAuthService", firebaseAuthService);
        TestUtils.setField(contractService, "aiService", aiService);
        TestUtils.setField(contractService, "ethereumService", ethereumService);
        TestUtils.setField(contractService, "solidityCompiler", solidityCompiler);
        TestUtils.setField(contractService, "firestore", firestore);

        // Common auth
        lenient().when(firebaseAuthService.getCurrentUserId()).thenReturn("user123");
        lenient().when(documentSnapshot.getString("userId")).thenReturn("user123");
    }

    @Test
    void saveUploadedContract_shouldSaveContract() {
		String contractText = "Sample contract text";
		String contractId = contractService.saveUploadedContract(contractText);

		assertNotNull(contractId);
        ArgumentCaptor<DigitalizedContract> captor = ArgumentCaptor.forClass(DigitalizedContract.class);
        verify(documentReference).set(captor.capture());

        DigitalizedContract captured = captor.getValue();
        assertEquals(contractText, captured.contractText());
        assertEquals("user123", captured.userId());
        assertEquals(ContractStatus.UPLOADED.name(), captured.status());
	}

    @Test
    void getContractStatus_shouldReturnStatus() {
		when(documentSnapshot.getString("status")).thenReturn(ContractStatus.UPLOADED.name());

		DeploymentStatusResponseDTO status = contractService.getContractStatus("contractId");

        assertNotNull(status);
        assertEquals("contractId", status.getContractId());
		assertEquals(ContractStatus.UPLOADED.name(), status.getStatus());
		verify(documentReference).get();
	}

    @Test
    void getContractStatus_shouldThrowIfContractNotFound() {
		when(documentSnapshot.exists()).thenReturn(false);

		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> contractService.getContractStatus("missingId"));

		assertTrue(ex.getCause().getMessage().contains("Contract not found"));
	}

    @Test
    void getContractStatus_shouldThrowIfUserIsNotOwner() {
		when(documentSnapshot.getString("userId")).thenReturn("otherUser");

		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> contractService.getContractStatus("contractId"));

		assertTrue(ex.getCause().getMessage().contains("not authorized"));
	}

    @Test
    void extractClauses_shouldReturnCachedClauses() {
        List<String> cached = List.of("Clause A", "Clause B");

        when(documentSnapshot.contains("extractedClauses")).thenReturn(true);
        when(documentSnapshot.get("extractedClauses")).thenReturn(cached);

        List<String> result = contractService.extractClauses("contractId");

        assertEquals(cached, result);
        verify(aiService, never()).extractClauses(any());
    }

    @Test
    void extractClauses_shouldExtractAndStoreClauses() {
        String contractText = "Full contract text";
        List<String> aiClauses = List.of("Clause X", "Clause Y");

        when(documentSnapshot.contains("extractedClauses")).thenReturn(false);
        when(documentSnapshot.getString("contractText")).thenReturn(contractText);
        when(aiService.extractClauses(contractText)).thenReturn(aiClauses);

        List<String> result = contractService.extractClauses("contractId");

        assertEquals(aiClauses, result);
        verify(documentReference).update(argThat(map ->
                map.get("extractedClauses").equals(aiClauses)
                        && map.get("status").equals(ContractStatus.CLAUSES_EXTRACTED.name())));
    }

    @Test
    void generateSolidity_shouldCompileAndStoreSolidity() throws IOException, InterruptedException {
        List<String> clauses = List.of("Clause 1", "Clause 2");
        String expectedSource = """
                // SPDX-License-Identifier: MIT
                pragma solidity ^0.8.0;

                contract LegalContract {

                // Clause 1
                // Clause 2
                }""";

        CompilationResultDTO mockResult = CompilationResultDTO.builder()
                .bin("0xabc123")
                .abi("[{\"type\":\"constructor\"}]")
                .build();

        when(documentSnapshot.get("extractedClauses")).thenReturn(clauses);
        when(solidityCompiler.compile(anyString())).thenReturn(mockResult);

        String solidity = contractService.generateSolidity("contractId");

        assertNotNull(solidity);
        assertTrue(solidity.contains("contract LegalContract"));

        verify(documentReference).update(argThat(map ->
                expectedSource.equals(map.get("soliditySource")) &&
                        "0xabc123".equals(map.get("binary")) &&
                        map.get("abi") != null &&
                        ContractStatus.SOLIDITY_GENERATED.name().equals(map.get("status"))
        ));
    }

    @Test
    void deployContractWithParams_shouldDeployAndUpdateStatus() throws Exception {
        when(documentSnapshot.getString("binary")).thenReturn("608060...");
        when(ethereumService.deployContractWithConstructor(eq("608060..."), anyList()))
                .thenReturn("0xContractAddress");

        String deployedAddress = contractService.deployContractWithParams("contractId", List.of("param"));

        assertEquals("0xContractAddress", deployedAddress);
        verify(documentReference, times(1)).update(
				"status", ContractStatus.DEPLOYED.name(),
				"deployedAddress", "0xContractAddress"
		);
    }

    @Test
    void extractClauses_shouldThrowIfContractNotFound() {
        when(documentSnapshot.exists()).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> contractService.extractClauses("missingId"));

        assertTrue(ex.getMessage().contains("Clause extraction failed"));
        assertTrue(ex.getCause().getMessage().contains("Contract not found"));
    }

    @Test
    void extractClauses_shouldThrowIfUserIsNotOwner() {
        when(documentSnapshot.getString("userId")).thenReturn("otherUser");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> contractService.extractClauses("contractId"));

        assertTrue(ex.getMessage().contains("Clause extraction failed"));
        assertTrue(ex.getCause().getMessage().contains("not authorized"));
    }

    @Test
    void generateSolidity_shouldThrowIfNoClauses() {
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.get("extractedClauses")).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> contractService.generateSolidity("contractId"));

        assertTrue(ex.getMessage().contains("Failed to generate Solidity"));
        assertTrue(ex.getCause().getMessage().contains("No extracted clauses"));
    }

    @Test
    void deployContractWithParams_shouldFailIfBinaryMissing() {
        when(documentSnapshot.getString("binary")).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> contractService.deployContractWithParams("contractId", List.of()));

        assertTrue(ex.getMessage().contains("Deployment failed"));
        assertTrue(ex.getCause().getMessage().contains("Contract binary not found or empty in Firestore"));
    }
}
