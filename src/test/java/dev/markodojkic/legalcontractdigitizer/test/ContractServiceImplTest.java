package dev.markodojkic.legalcontractdigitizer.test;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import dev.markodojkic.legalcontractdigitizer.dto.CompilationResultDTO;
import dev.markodojkic.legalcontractdigitizer.enums.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractServiceImplTest {

	@InjectMocks
	private ContractServiceImpl contractService;

	@Mock private FirebaseAuthService firebaseAuthService;
	@Mock private AIService aiService;
	@Mock private EthereumService ethereumService;
	@Mock private SolidityCompiler solidityCompiler;

	@Mock private Firestore firestore;
	@Mock private CollectionReference collectionReference;
	@Mock private DocumentReference documentReference;
	@Mock private DocumentSnapshot documentSnapshot;
	@Mock private ApiFuture<DocumentSnapshot> apiFuture;
	@Mock private ApiFuture<WriteResult> writeResultApiFuture;

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);

		// Firestore mocking
		when(documentSnapshot.exists()).thenReturn(true);
		when(firestore.collection("contracts")).thenReturn(collectionReference);
		when(collectionReference.document(anyString())).thenReturn(documentReference);
		when(documentReference.get()).thenReturn(apiFuture);
		when(apiFuture.get()).thenReturn(documentSnapshot);
		when(documentReference.update(anyMap())).thenReturn(writeResultApiFuture);
		when(writeResultApiFuture.get()).thenReturn(mock(WriteResult.class));

		// Inject Firestore manually since @PostConstruct won't be called in test
		try {
			var field = ContractServiceImpl.class.getDeclaredField("db");
			field.setAccessible(true);
			field.set(contractService, firestore);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		// Common auth
		when(firebaseAuthService.getCurrentUserId()).thenReturn("user123");
		when(documentSnapshot.getString("userId")).thenReturn("user123");
	}

	@Test
	void extractClauses_shouldReturnCachedClauses() throws Exception {
		List<String> cached = List.of("Clause A", "Clause B");

		when(documentSnapshot.contains("extractedClauses")).thenReturn(true);
		when(documentSnapshot.get("extractedClauses")).thenReturn(cached);

		List<String> result = contractService.extractClauses("contractId");

		assertEquals(cached, result);
		verify(aiService, never()).extractClauses(any());
	}

	@Test
	void extractClauses_shouldExtractAndStoreClauses() throws Exception {
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
	void generateSolidity_shouldCompileAndStoreSolidity() throws Exception {
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
		verify(documentReference).update(argThat(map ->
				map.get("status").equals(ContractStatus.DEPLOYED.name()) &&
						map.get("deployedAddress").equals("0xContractAddress")
		));
	}

	@Test
	void extractClauses_shouldThrowIfContractNotFound() throws Exception {
		when(documentSnapshot.exists()).thenReturn(false);

		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> contractService.extractClauses("missingId"));

		assertTrue(ex.getMessage().contains("Clause extraction failed:"));
		assertTrue(ex.getMessage().contains("Contract not found"));
	}

	@Test
	void extractClauses_shouldThrowIfUserIsNotOwner() throws Exception {
		when(documentSnapshot.getString("userId")).thenReturn("otherUser");

		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> contractService.extractClauses("contractId"));

		assertTrue(ex.getMessage().contains("Clause extraction failed:"));
		assertTrue(ex.getMessage().contains("not authorized"));
	}

	@Test
	void generateSolidity_shouldThrowIfNoClauses() throws Exception {
		when(documentSnapshot.exists()).thenReturn(true);
		when(documentSnapshot.get("extractedClauses")).thenReturn(null);

		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> contractService.generateSolidity("contractId"));

		assertTrue(ex.getMessage().contains("Failed to generate Solidity:"));
		assertTrue(ex.getMessage().contains("No extracted clauses"));
	}

	@Test
	void deployContractWithParams_shouldFailIfBinaryMissing() throws Exception {
		when(documentSnapshot.getString("binary")).thenReturn(null);

		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> contractService.deployContractWithParams("contractId", List.of()));

		assertTrue(ex.getMessage().contains("Deployment failed"));
		assertTrue(ex.getMessage().contains("Contract binary not found"));
	}
}
