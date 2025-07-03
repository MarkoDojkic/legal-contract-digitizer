package dev.markodojkic.legalcontractdigitizer.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import dev.markodojkic.legalcontractdigitizer.model.CompilationResult;
import dev.markodojkic.legalcontractdigitizer.model.GasEstimateResponseDTO;
import dev.markodojkic.legalcontractdigitizer.model.ContractDeploymentContext;
import dev.markodojkic.legalcontractdigitizer.model.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.model.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.exception.*;
import dev.markodojkic.legalcontractdigitizer.service.IAIService;
import dev.markodojkic.legalcontractdigitizer.service.IEthereumService;
import dev.markodojkic.legalcontractdigitizer.service.IContractService;
import dev.markodojkic.legalcontractdigitizer.util.AuthSession;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractServiceImpl implements IContractService {

	@Value("${ethereum.solidityCompilerExecutable}")
	private String solidityCompilerExecutable;

	private static final String BINARY = "binary", CONTRACT_TEXT = "contractText", USER_ID = "userId", DEPLOYED_ADDRESS = "deployedAddress", SOLIDITY_SOURCE = "soliditySource", STATUS = "status", EXTRACTED_CLAUSES = "extractedClauses", CONTRACTS = "contracts";
	private final ObjectMapper objectMapper;
	private final IAIService aiService;
	private final IEthereumService ethereumService;
	private Firestore firestore;

	@PostConstruct
	public void init() {
		firestore = FirestoreClient.getFirestore();
	}

	@Override
	public String saveUploadedContract(String contractText) {
		String userId = AuthSession.getCurrentUserId();
		String contractId = UUID.randomUUID().toString();
		ContractStatus initialStatus = ContractStatus.UPLOADED;

		DocumentReference docRef = firestore.collection(CONTRACTS).document(contractId);
		docRef.set(DigitalizedContract.builder()
				.id(contractId)
				.userId(userId)
				.contractText(contractText)
				.status(initialStatus)
				.build());

		log.debug("Contract saved with ID: {} by user: {} with status: {}", contractId, userId, initialStatus);
		return contractId;
	}

	@Override
	public List<DigitalizedContract> listContractsForUser() {
		List<DigitalizedContract> contracts = new ArrayList<>();
		try {
			ApiFuture<QuerySnapshot> future = firestore.collection(CONTRACTS)
					.whereEqualTo(USER_ID, AuthSession.getCurrentUserId())
					.get();

			List<QueryDocumentSnapshot> documents = future.get().getDocuments();

			for (QueryDocumentSnapshot doc : documents) {
				String id = doc.getId();
				String contractUserId = doc.getString(USER_ID);
				String contractText = doc.getString(CONTRACT_TEXT);
				ContractStatus status = ContractStatus.valueOf(doc.getString(STATUS));

				List<String> extractedClauses = doc.contains(EXTRACTED_CLAUSES) ? (List<String>) doc.get(EXTRACTED_CLAUSES) : null;

				String soliditySource = doc.getString(SOLIDITY_SOURCE);
				String binary = doc.getString(BINARY);
				String abi = doc.getString("abi");
				String deployedAddress = doc.getString(DEPLOYED_ADDRESS);

				if(status == ContractStatus.CONFIRMED){
					status = ethereumService.doesSmartContractExist(deployedAddress) ?
							ContractStatus.CONFIRMED : ContractStatus.TERMINATED;
					updateContractStatus(deployedAddress, status);
				}

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
			log.error("Failed to list contracts for userId={}", AuthSession.getCurrentUserId(), e);
			throw new ContractReadException("Failed to list contracts");
		}
		return contracts;
	}

	@Override
	public void updateContractStatus(String deploymentAddress, ContractStatus newStatus) throws ContractNotFoundException, UnauthorizedAccessException {
		QuerySnapshot querySnapshot;
		try {
			querySnapshot = firestore.collection(CONTRACTS)
					.whereEqualTo(DEPLOYED_ADDRESS, deploymentAddress)
					.limit(1)
					.get()
					.get();
		} catch (Exception e) {
			log.error("No contract found with deployed address: {}", deploymentAddress, e);
			throw new ContractNotFoundException("No contract found with deployed address: " + deploymentAddress);
		}

		if (querySnapshot.isEmpty()) throw new ContractNotFoundException("No contract found with deployed address: " + deploymentAddress);

		verifyOwnership(querySnapshot.getDocuments().getFirst());

		querySnapshot.getDocuments().getFirst().getReference().update(STATUS, newStatus.name());

		log.debug("Updated contract status to {} for deployment address: {}", newStatus.name(), deploymentAddress);
	}

	@Override
	public void deleteIfNotDeployed(String contractId) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, ContractAlreadyConfirmedException {
		DocumentReference docRef = firestore.collection(CONTRACTS).document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, docRef);

		if (ContractStatus.valueOf(snapshot.getString(STATUS)).compareTo(ContractStatus.DEPLOYED) < 0) {
			docRef.delete();
			log.debug("Deleted contract with ID {}", contractId);
		} else {
			throw new ContractAlreadyConfirmedException("Cannot delete contract that is already confirmed");
		}
	}

	@Override
	public DigitalizedContract getContract(String contractId) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException {
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, firestore.collection(CONTRACTS).document(contractId));

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

	@Override
	public List<String> extractClauses(String contractId) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, ClausesExtractionException {
		DocumentReference docRef = firestore.collection(CONTRACTS).document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, firestore.collection(CONTRACTS).document(contractId));

		List<String> cached = (List<String>) snapshot.get(EXTRACTED_CLAUSES);
		if (cached != null && !cached.isEmpty()) {
			log.debug("Using cached clauses for contract ID: {}", contractId);
			return cached;
		}

		String contractText = snapshot.getString(CONTRACT_TEXT);
		if (contractText == null || contractText.isEmpty()) {
			log.debug("Contract text is empty or null for contract ID: {}", contractId);
			throw new ClausesExtractionException("Contract text is empty or null");
		}

		log.debug("Extracting clauses for contract ID: {}", contractId);
		List<String> contractClauses;
		try {
			contractClauses = aiService.extractClauses(contractText);
		} catch (Exception e) {
			log.error("Failed to extract clauses for contract ID: {}", contractId, e);
			throw new ClausesExtractionException("AI service failed to extract clauses from text:\n" + e.getLocalizedMessage());
		}

		if (contractClauses == null || contractClauses.isEmpty()) {
			log.debug("No clauses extracted for contract ID: {}", contractId);
			throw new ClausesExtractionException("No clauses extracted");
		}

		docRef.update(Map.of(EXTRACTED_CLAUSES, contractClauses, STATUS, ContractStatus.CLAUSES_EXTRACTED.name()));

		log.debug("Successfully extracted {} clauses for contract ID: {}", contractClauses.size(), contractId);
		return contractClauses;
	}

	@Override
	@SuppressWarnings("unchecked")
	public String generateSolidity(String contractId) throws ContractNotFoundException, UnauthorizedAccessException, ClausesExtractionException, CompilationException, SolidityGenerationException {
		DocumentReference docRef = firestore.collection(CONTRACTS).document(contractId);
		DocumentSnapshot snapshot = getDocumentOrThrow(contractId, docRef);

		List<String> clauses = (List<String>) snapshot.get(EXTRACTED_CLAUSES);
		if (clauses == null || clauses.isEmpty()) {
			log.debug("No clauses extracted for contract ID: {}", contractId);
			throw new ClausesExtractionException("No clauses extracted");
		}

		String cachedSoliditySource = snapshot.getString(SOLIDITY_SOURCE);
		if (cachedSoliditySource != null && !cachedSoliditySource.isEmpty()) {
			log.debug("Using cached solidity code for contract ID: {}", contractId);
			return compileAndUpdateDocument(docRef, snapshot, cachedSoliditySource);
		}

		log.debug("Generating solidity code for contract ID: {}", contractId);
		String soliditySource;
		try {
			soliditySource = aiService.generateSolidityContract(clauses);
		} catch (Exception e) {
			log.error("Failed to generate solidity code for contract ID: {}", contractId, e);
			throw new SolidityGenerationException(e.getLocalizedMessage());
		}

		if (soliditySource == null || soliditySource.isEmpty()) {
			log.error("Generated solidity code is empty: {}", contractId);
			throw new SolidityGenerationException("Generated solidity code is empty");
		}

		// Update document with the generated Solidity source
		docRef.update(Map.of(
				SOLIDITY_SOURCE, soliditySource,
				STATUS, ContractStatus.SOLIDITY_PREPARED.name()
		));
		log.debug("Successfully updated document with Solidity source for contract ID: {}", contractId);

		// Compile and update document with the binary and ABI
		return compileAndUpdateDocument(docRef, snapshot, soliditySource);
	}

	@Override
	public String deployContractWithParams(String contractId, List<Object> constructorParams, Credentials credentials) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, InvalidContractBinaryException, DeploymentFailedException {
		ContractDeploymentContext context = prepareDeploymentContext(contractId, constructorParams);

		String contractAddress = ethereumService.deployCompiledContract(
				context.ethContext().contractBinary(),
				context.ethContext().encodedConstructor(),
				credentials
		);

		context.contractRef().update(Map.of(STATUS, ContractStatus.DEPLOYED.name(), DEPLOYED_ADDRESS, contractAddress));

		log.debug("User {} deployed contract {} at address {}", AuthSession.getCurrentUserId(), contractId, contractAddress);
		return contractAddress;
	}

	@Override
	public GasEstimateResponseDTO estimateGasForDeployment(String contractId, List<Object> constructorParams, String deployerWalletAddress) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, InvalidContractBinaryException, GasEstimationFailedException {
		ContractDeploymentContext context = prepareDeploymentContext(contractId, constructorParams);
		Pair<BigInteger, BigInteger> estimateGasForDeployment = ethereumService.estimateGasForDeployment(context.ethContext().contractBinary(), context.ethContext().encodedConstructor(), deployerWalletAddress);

		return new GasEstimateResponseDTO("", estimateGasForDeployment.getLeft(), estimateGasForDeployment.getRight());
	}

	private String compileAndUpdateDocument(DocumentReference docRef, DocumentSnapshot snapshot, String soliditySource) throws CompilationException {
		CompilationResult result;
		try {
			log.debug("Compiling solidity code for contract ID: {}", snapshot.getId());
			result = compile(soliditySource);
		} catch (CompilationException e) {
			log.error("Solidity compilation failed for contract ID: {}", snapshot.getId(), e);
			throw new CompilationException(e.getLocalizedMessage());
		}

		docRef.update(Map.of(
				BINARY, result.bin(),
				"abi", result.abi(),
				STATUS, ContractStatus.SOLIDITY_GENERATED.name()
		));
		log.debug("Successfully compiled Solidity source and updated contract ID: {}", snapshot.getId());

		return "Successfully compiled Solidity source";
	}


	private DocumentSnapshot getDocumentOrThrow(String contractId, DocumentReference docRef) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException {
		try {
			DocumentSnapshot snapshot = docRef.get().get();
			if (!snapshot.exists()) {
				log.debug("Contract not found with ID {}", contractId);
				throw new ContractNotFoundException("Contract not found: " + contractId);
			}
			verifyOwnership(snapshot);
			return snapshot;
		} catch (InterruptedException | ExecutionException e) {
			log.error("Error retrieving contract {}", contractId, e);
			throw new ContractReadException("Error retrieving contract: " + contractId);
		}
	}

	private void verifyOwnership(DocumentSnapshot snapshot) throws UnauthorizedAccessException {
		String contractUserId = snapshot.getString(USER_ID);
		String currentUserId = AuthSession.getCurrentUserId();

		if (contractUserId == null || !contractUserId.equals(currentUserId)) throw new UnauthorizedAccessException("You are not authorized to access this contract.");
	}

	private ContractDeploymentContext prepareDeploymentContext(String contractId, List<Object> constructorParams) throws ContractNotFoundException, UnauthorizedAccessException, ContractReadException, InvalidContractBinaryException {
		DocumentReference contractRef = firestore.collection(CONTRACTS).document(contractId);

		String contractBinary = getDocumentOrThrow(contractId, contractRef).getString(BINARY);
		if (contractBinary == null || contractBinary.isEmpty()) throw new InvalidContractBinaryException("Contract binary not found or empty in Firestore");

		return new ContractDeploymentContext(ethereumService.buildDeploymentContext(contractBinary, constructorParams), contractRef);
	}

	private CompilationResult compile(String soliditySource) throws CompilationException {
		Path sourceFile = null;
		try {
			sourceFile = Files.createTempFile("contract", ".sol");

			Files.writeString(sourceFile, soliditySource);

			Process process = new ProcessBuilder(solidityCompilerExecutable, "--combined-json", "abi,bin", sourceFile.toAbsolutePath().toString()).start();

			if (process.waitFor() != 0) throw new CompilationException(new String(process.getErrorStream().readAllBytes()));

			JsonNode contractsNode = objectMapper.readTree(new String(process.getInputStream().readAllBytes())).path(CONTRACTS);
			if (contractsNode.isEmpty()) throw new CompilationException("No smart contracts found in compilation output");

			JsonNode contractNode = contractsNode.get(contractsNode.fieldNames().next());

			return new CompilationResult(contractNode.get("bin").asText(), contractNode.get("abi").toString());

		} catch (Exception e){
			if(e instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new CompilationException(e.getLocalizedMessage());
		} finally {
			if(sourceFile != null) try { Files.deleteIfExists(sourceFile); } catch (IOException _) {}
		}
	}
}