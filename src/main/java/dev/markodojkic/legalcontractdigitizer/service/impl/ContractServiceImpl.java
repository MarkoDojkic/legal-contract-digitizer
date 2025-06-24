package dev.markodojkic.legalcontractdigitizer.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import dev.markodojkic.legalcontractdigitizer.dto.CompilationResultDTO;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.ContractDeploymentContext;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.EthereumContractContext;
import dev.markodojkic.legalcontractdigitizer.exception.*;
import dev.markodojkic.legalcontractdigitizer.service.AIService;
import dev.markodojkic.legalcontractdigitizer.service.EthereumService;
import dev.markodojkic.legalcontractdigitizer.service.IContractService;
import dev.markodojkic.legalcontractdigitizer.service.TokenAuthService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractServiceImpl implements IContractService {

	private final TokenAuthService authService;
	private final AIService aiService;
	private final EthereumService ethereumService;
	private Firestore firestore;

	@PostConstruct
	public void init() {
		firestore = FirestoreClient.getFirestore();
	}

	@Override
	public String saveUploadedContract(String contractText) {
		String userId = authService.getCurrentUserId();
		String contractId = UUID.randomUUID().toString();
		ContractStatus initialStatus = ContractStatus.UPLOADED;

		DocumentReference docRef = firestore.collection("contracts").document(contractId);
		docRef.set(DigitalizedContract.builder()
				.id(contractId)
				.userId(userId)
				.contractText(contractText)
				.status(initialStatus)
				.build());

		log.info("Contract saved with ID: {} by user: {} with status: {}", contractId, userId, initialStatus);
		return contractId;
	}

	@Override
	public List<DigitalizedContract> listContractsForUser(String userId) {
		List<DigitalizedContract> contracts = new ArrayList<>();
		try {
			ApiFuture<QuerySnapshot> future = firestore.collection("contracts")
					.whereEqualTo("userId", userId)
					.get();

			List<QueryDocumentSnapshot> documents = future.get().getDocuments();

			for (QueryDocumentSnapshot doc : documents) {
				String id = doc.getId();
				String contractUserId = doc.getString("userId");
				String contractText = doc.getString("contractText");
				ContractStatus status = ContractStatus.valueOf(doc.getString("status"));

				List<String> extractedClauses = doc.contains("extractedClauses")
						? (List<String>) doc.get("extractedClauses") : null;

				String soliditySource = doc.getString("soliditySource");
				String binary = doc.getString("binary");
				String abi = doc.getString("abi");
				String deployedAddress = doc.getString("deployedAddress");

				contracts.add(new DigitalizedContract(
						id,
						contractUserId,
						contractText,
						status,
						extractedClauses,
						soliditySource,
						binary,
						abi,
						deployedAddress
				));
			}
		} catch (Exception e) {
			log.error("Failed to list contracts for userId={}", userId, e);
		}
		return contracts;
	}

	@Override
	public void deleteIfNotConfirmed(String contractId) {
		DocumentReference docRef = firestore.collection("contracts").document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, docRef);

		if (!ContractStatus.valueOf(snapshot.getString("status")).equals(ContractStatus.CONFIRMED)) {
			docRef.delete();
			log.info("Deleted contract with ID {}", contractId);
		} else {
			throw new ContractAlreadyConfirmedException("Cannot delete contract that is already confirmed");
		}
	}

	@Override
	public DigitalizedContract getContract(String contractId) {
		DocumentReference docRef = firestore.collection("contracts").document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, docRef);

		return mapSnapshotToContract(snapshot);
	}

	@Override
	public List<String> extractClauses(String contractId) {
		DocumentReference docRef = firestore.collection("contracts").document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, docRef);

		// Retrieve cached clauses if available
		List<String> cached = (List<String>) snapshot.get("extractedClauses");
		if (cached != null && !cached.isEmpty()) {
			log.info("Using cached clauses for contract ID: {}", contractId);
			return cached;
		}

		// Extract clauses using AI service
		String text = snapshot.getString("contractText");
		if (text == null || text.isEmpty()) {
			log.error("Contract text is empty or null for contract ID: {}", contractId);
			throw new ContractNotFoundException(contractId);
		}

		log.info("Extracting clauses for contract ID: {}", contractId);
		List<String> clauses;
		try {
			clauses = aiService.extractClauses(text);
		} catch (Exception e) {
			log.error("Failed to extract clauses for contract ID: {}", contractId, e);
			throw new ClausesExtractionException(contractId, e);
		}

		if (clauses == null || clauses.isEmpty()) {
			log.error("No clauses extracted for contract ID: {}", contractId);
			throw new ClausesExtractionException(contractId, null);
		}

		docRef.update(Map.of(
				"extractedClauses", clauses,
				"status", ContractStatus.CLAUSES_EXTRACTED.name()
		));

		log.info("Successfully extracted {} clauses for contract ID: {}", clauses.size(), contractId);
		return clauses;
	}

	@Override
	public String generateSolidity(String contractId) {
		DocumentReference docRef = firestore.collection("contracts").document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, docRef);

		// Retrieve the extracted clauses
		List<String> clauses = (List<String>) snapshot.get("extractedClauses");
		if (clauses == null || clauses.isEmpty()) {
			log.error("No extracted clauses found for contract ID: {}", contractId);
			throw new ContractNotFoundException(contractId);
		}

		// Check if we already have Solidity source cached
		String cachedSoliditySource = snapshot.getString("soliditySource");
		if (cachedSoliditySource != null && !cachedSoliditySource.isEmpty()) {
			log.info("Using cached Solidity contract for contract ID: {}", contractId);

			// Compile cached Solidity source if it exists
			return compileAndUpdateDocument(docRef, snapshot, cachedSoliditySource);
		}

		// Generate Solidity contract using AI service
		log.info("Generating Solidity contract for contract ID: {}", contractId);
		String soliditySource;
		try {
			soliditySource = aiService.generateSolidityContract(clauses, true);
		} catch (Exception e) {
			log.error("Failed to generate Solidity contract for contract ID: {}", contractId, e);
			throw new SolidityGenerationException(contractId, e);
		}

		if (soliditySource == null || soliditySource.isEmpty()) {
			log.error("Solidity generation failed for contract ID: {}", contractId);
			throw new SolidityGenerationException(contractId, null);
		}

		// Update document with the generated Solidity source
		docRef.update(Map.of(
				"soliditySource", soliditySource,
				"status", ContractStatus.SOLIDITY_PREPARED.name()
		));
		log.info("Successfully updated document with Solidity source for contract ID: {}", contractId);

		// Compile and update document with the binary and ABI
		return compileAndUpdateDocument(docRef, snapshot, soliditySource);
	}

	private String compileAndUpdateDocument(DocumentReference docRef, DocumentSnapshot snapshot, String soliditySource) {
		// Compile the Solidity source code
		CompilationResultDTO result;
		try {
			log.info("Compiling Solidity contract for contract ID: {}", snapshot.getId());
			result = compile(soliditySource);
		} catch (IOException | InterruptedException e) {
			log.error("Solidity compilation failed for contract ID: {}", snapshot.getId(), e);
			throw new CompilationException(snapshot.getId(), e);
		}

		// Update the document with the binary and ABI after compilation
		docRef.update(Map.of(
				"binary", result.getBin(),
				"abi", result.getAbi(),
				"status", ContractStatus.SOLIDITY_GENERATED.name()
		));
		log.info("Successfully compiled and updated contract ID: {}", snapshot.getId());

		return soliditySource;
	}

	@Override
	public String deployContractWithParams(String contractId, List<Object> constructorParams)
			throws ContractNotFoundException, UnauthorizedAccessException,
			IllegalStateException, InvalidContractBinaryException, DeploymentFailedException {

		ContractDeploymentContext context = prepareDeploymentContext(contractId, constructorParams);

		String contractAddress = ethereumService.deployCompiledContract(
				context.ethContext().contractBinary(),
				context.ethContext().encodedConstructor()
		);

		context.contractRef().update(
				Map.of(
						"status", ContractStatus.DEPLOYED.name(),
						"deployedAddress", contractAddress
				)
		);

		log.info("User {} deployed contract {} at address {}", context.userId(), contractId, contractAddress);
		return contractAddress;
	}

	@Override
	public BigInteger estimateGasForDeployment(String contractId, List<Object> constructorParams)
			throws ContractNotFoundException, UnauthorizedAccessException,
			IllegalStateException, InvalidContractBinaryException, GasEstimationFailedException {

		ContractDeploymentContext context = prepareDeploymentContext(contractId, constructorParams);

		BigInteger estimatedGas = ethereumService.estimateGasForDeployment(
				context.ethContext().contractBinary(),
				context.ethContext().encodedConstructor()
		);

		log.info("Estimated gas for contract {}: {}", contractId, estimatedGas);
		return estimatedGas;
	}

	// === Helper methods ===

	private DocumentSnapshot getDocumentOrThrow(String contractId, DocumentReference docRef) {
		try {
			DocumentSnapshot snapshot = docRef.get().get();
			if (!snapshot.exists()) {
				log.warn("Contract not found with ID {}", contractId);
				throw new ContractNotFoundException("Contract not found: " + contractId);
			}
			verifyOwnership(snapshot);
			return snapshot;
		} catch (InterruptedException | java.util.concurrent.ExecutionException e) {
			log.error("Error retrieving contract {}", contractId, e);
			throw new RuntimeException("Error retrieving contract: " + contractId, e);
		}
	}

	private void verifyOwnership(DocumentSnapshot snapshot) {
		String contractUserId = snapshot.getString("userId");
		String currentUserId = authService.getCurrentUserId();

		if (contractUserId == null || !contractUserId.equals(currentUserId)) {
			throw new UnauthorizedAccessException("You are not authorized to access this contract.");
		}
	}

	private DigitalizedContract mapSnapshotToContract(DocumentSnapshot snapshot) {
		return new DigitalizedContract(
				snapshot.getId(),
				snapshot.getString("userId"),
				snapshot.getString("contractText"),
				ContractStatus.valueOf(snapshot.getString("status")),
				snapshot.contains("extractedClauses") ? (List<String>) snapshot.get("extractedClauses") : null,
				snapshot.getString("soliditySource"),
				snapshot.getString("binary"),
				snapshot.getString("abi"),
				snapshot.getString("deployedAddress")
		);
	}

	private ContractDeploymentContext prepareDeploymentContext(String contractId, List<Object> constructorParams) throws InvalidContractBinaryException {
		String userId = authService.getCurrentUserId();
		if (userId == null) {
			throw new IllegalStateException("User not authenticated");
		}

		DocumentReference contractRef = firestore.collection("contracts").document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, contractRef);

		String contractBinary = snapshot.getString("binary");
		if (contractBinary == null || contractBinary.isEmpty()) {
			throw new IllegalStateException("Contract binary not found or empty in Firestore");
		}

		EthereumContractContext ethContext = ethereumService.buildDeploymentContext(contractBinary, constructorParams);

		return new ContractDeploymentContext(userId, ethContext, contractRef);
	}

	private CompilationResultDTO compile(String soliditySource) throws IOException, InterruptedException {
		Path sourceFile = Files.createTempFile("contract", ".sol");
		try {
			Files.writeString(sourceFile, soliditySource);

			ProcessBuilder pb = new ProcessBuilder(
					"C:\\Users\\marko.dojkic\\Desktop\\Spring AI Project\\solc-windows.exe",
					"--combined-json", "abi,bin",
					sourceFile.toAbsolutePath().toString()
			);
			Process process = pb.start();

			String output = new String(process.getInputStream().readAllBytes());
			int exitCode = process.waitFor();

			if (exitCode != 0) {
				String err = new String(process.getErrorStream().readAllBytes());
				throw new RuntimeException("Solidity compilation failed: " + err);
			}

			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(output);
			JsonNode contractsNode = root.path("contracts");
			if (contractsNode.isEmpty()) {
				throw new RuntimeException("No contracts found in compilation output");
			}

			String contractKey = contractsNode.fieldNames().next();
			JsonNode contractNode = contractsNode.get(contractKey);

			String abi = contractNode.get("abi").toString();
			String bin = contractNode.get("bin").asText();

			return CompilationResultDTO.builder().abi(abi).bin(bin).build();
		} finally {
			Files.deleteIfExists(sourceFile);
		}
	}
}