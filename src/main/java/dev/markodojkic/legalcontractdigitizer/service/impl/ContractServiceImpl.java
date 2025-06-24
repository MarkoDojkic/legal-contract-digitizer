package dev.markodojkic.legalcontractdigitizer.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import dev.markodojkic.legalcontractdigitizer.dto.CompilationResultDTO;
import dev.markodojkic.legalcontractdigitizer.enums_records.ContractDeploymentContext;
import dev.markodojkic.legalcontractdigitizer.enums_records.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.enums_records.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.enums_records.EthereumContractContext;
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

	public static final String BINARY = "binary";
	public static final String CONTRACT_TEXT = "contractText";
	public static final String USER_ID = "userId";
	public static final String DEPLOYED_ADDRESS = "deployedAddress";
	public static final String SOLIDITY_SOURCE = "soliditySource";
	public static final String STATUS = "status";
	public static final String EXTRACTED_CLAUSES = "extractedClauses";
	public static final String CONTRACTS = "contracts";
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

		DocumentReference docRef = firestore.collection(CONTRACTS).document(contractId);
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
			ApiFuture<QuerySnapshot> future = firestore.collection(CONTRACTS)
					.whereEqualTo(USER_ID, userId)
					.get();

			List<QueryDocumentSnapshot> documents = future.get().getDocuments();

			for (QueryDocumentSnapshot doc : documents) {
				String id = doc.getId();
				String contractUserId = doc.getString(USER_ID);
				String contractText = doc.getString(CONTRACT_TEXT);
				ContractStatus status = ContractStatus.valueOf(doc.getString(STATUS));

				List<String> extractedClauses = doc.contains(EXTRACTED_CLAUSES)
						? (List<String>) doc.get(EXTRACTED_CLAUSES) : null;

				String soliditySource = doc.getString(SOLIDITY_SOURCE);
				String binary = doc.getString(BINARY);
				String abi = doc.getString("abi");
				String deployedAddress = doc.getString(DEPLOYED_ADDRESS);

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
			Thread.currentThread().interrupt();
			log.error("Failed to list contracts for userId={}", userId, e);
		}
		return contracts;
	}

	@Override
	public void updateContractStatusToConfirmed(String deploymentAddress)
			throws ContractNotFoundException, IllegalStateException, IllegalArgumentException {

		// Query contract by deployedAddress
		QuerySnapshot querySnapshot;
		try {
			querySnapshot = firestore.collection(CONTRACTS)
					.whereEqualTo(DEPLOYED_ADDRESS, deploymentAddress)
					.limit(1)
					.get()
					.get();
		} catch (Exception e) {
			Thread.currentThread().interrupt();
			throw new IllegalArgumentException("Failed to query Firestore", e.getCause());
		}

		if (querySnapshot.isEmpty()) {
			throw new ContractNotFoundException("No contract found with deployed address: " + deploymentAddress);
		}

		DocumentSnapshot snapshot = querySnapshot.getDocuments().getFirst();
		DocumentReference docRef = snapshot.getReference();

		ContractStatus currentStatus = ContractStatus.valueOf(snapshot.getString(STATUS));

		if (currentStatus == ContractStatus.DEPLOYED) {
			docRef.update(STATUS, ContractStatus.CONFIRMED.name());
			log.info("Updated contract status to CONFIRMED for deployment address: {}", deploymentAddress);
		} else {
			log.warn("Cannot update status to CONFIRMED, current status is: {}", currentStatus);
			throw new IllegalStateException("Contract is not in a deployable state");
		}
	}

	@Override
	public void deleteIfNotConfirmed(String contractId) {
		DocumentReference docRef = firestore.collection(CONTRACTS).document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, docRef);

		if (!ContractStatus.valueOf(snapshot.getString(STATUS)).equals(ContractStatus.CONFIRMED)) {
			docRef.delete();
			log.info("Deleted contract with ID {}", contractId);
		} else {
			throw new ContractAlreadyConfirmedException("Cannot delete contract that is already confirmed");
		}
	}

	@Override
	public DigitalizedContract getContract(String contractId) {
		DocumentReference docRef = firestore.collection(CONTRACTS).document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, docRef);

		return mapSnapshotToContract(snapshot);
	}

	@Override
	public List<String> extractClauses(String contractId) {
		DocumentReference docRef = firestore.collection(CONTRACTS).document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, docRef);

		// Retrieve cached clauses if available
		List<String> cached = (List<String>) snapshot.get(EXTRACTED_CLAUSES);
		if (cached != null && !cached.isEmpty()) {
			log.info("Using cached clauses for contract ID: {}", contractId);
			return cached;
		}

		// Extract clauses using AI service
		String text = snapshot.getString(CONTRACT_TEXT);
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
				EXTRACTED_CLAUSES, clauses,
				STATUS, ContractStatus.CLAUSES_EXTRACTED.name()
		));

		log.info("Successfully extracted {} clauses for contract ID: {}", clauses.size(), contractId);
		return clauses;
	}

	@Override
	public String generateSolidity(String contractId) {
		DocumentReference docRef = firestore.collection(CONTRACTS).document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, docRef);

		// Retrieve the extracted clauses
		List<String> clauses = (List<String>) snapshot.get(EXTRACTED_CLAUSES);
		if (clauses == null || clauses.isEmpty()) {
			log.error("No extracted clauses found for contract ID: {}", contractId);
			throw new ContractNotFoundException(contractId);
		}

		// Check if we already have Solidity source cached
		String cachedSoliditySource = snapshot.getString(SOLIDITY_SOURCE);
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
				SOLIDITY_SOURCE, soliditySource,
				STATUS, ContractStatus.SOLIDITY_PREPARED.name()
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
			Thread.currentThread().interrupt();
			log.error("Solidity compilation failed for contract ID: {}", snapshot.getId(), e);
			throw new CompilationException(snapshot.getId(), e);
		}

		// Update the document with the binary and ABI after compilation
		docRef.update(Map.of(
				BINARY, result.getBin(),
				"abi", result.getAbi(),
				STATUS, ContractStatus.SOLIDITY_GENERATED.name()
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
						STATUS, ContractStatus.DEPLOYED.name(),
						DEPLOYED_ADDRESS, contractAddress
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
			Thread.currentThread().interrupt();
			log.error("Error retrieving contract {}", contractId, e);
			throw new SolidityGenerationException("Error retrieving contract: " + contractId, e);
		}
	}

	private void verifyOwnership(DocumentSnapshot snapshot) {
		String contractUserId = snapshot.getString(USER_ID);
		String currentUserId = authService.getCurrentUserId();

		if (contractUserId == null || !contractUserId.equals(currentUserId)) {
			throw new UnauthorizedAccessException("You are not authorized to access this contract.");
		}
	}

	private DigitalizedContract mapSnapshotToContract(DocumentSnapshot snapshot) {
		return new DigitalizedContract(
				snapshot.getId(),
				snapshot.getString(USER_ID),
				snapshot.getString(CONTRACT_TEXT),
				ContractStatus.valueOf(snapshot.getString(STATUS)),
				snapshot.contains(EXTRACTED_CLAUSES) ? (List<String>) snapshot.get(EXTRACTED_CLAUSES) : null,
				snapshot.getString(SOLIDITY_SOURCE),
				snapshot.getString(BINARY),
				snapshot.getString("abi"),
				snapshot.getString(DEPLOYED_ADDRESS)
		);
	}

	private ContractDeploymentContext prepareDeploymentContext(String contractId, List<Object> constructorParams) throws InvalidContractBinaryException {
		String userId = authService.getCurrentUserId();
		if (userId == null) {
			throw new IllegalStateException("User not authenticated");
		}

		DocumentReference contractRef = firestore.collection(CONTRACTS).document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, contractRef);

		String contractBinary = snapshot.getString(BINARY);
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
					"solc",
					"--combined-json", "abi,bin",
					sourceFile.toAbsolutePath().toString()
			);
			Process process = pb.start();

			String output = new String(process.getInputStream().readAllBytes());
			int exitCode = process.waitFor();

			if (exitCode != 0) {
				String err = new String(process.getErrorStream().readAllBytes());
				throw new SolidityGenerationException("Solidity compilation failed: " + err, null);
			}

			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(output);
			JsonNode contractsNode = root.path(CONTRACTS);
			if (contractsNode.isEmpty()) {
				throw new SolidityGenerationException("No contracts found in compilation output", null);
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