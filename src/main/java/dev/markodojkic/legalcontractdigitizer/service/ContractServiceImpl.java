package dev.markodojkic.legalcontractdigitizer.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import dev.markodojkic.legalcontractdigitizer.dto.CompilationResultDTO;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.ContractDeploymentContext;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.EthereumContractContext;
import dev.markodojkic.legalcontractdigitizer.util.SolidityCompiler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
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
	private final SolidityCompiler solidityCompiler;
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
		docRef.set(DigitalizedContract.builder().id(contractId).userId(userId).contractText(contractText).status(initialStatus).build());

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
	public DigitalizedContract getContract(String contractId) {
		try {
			DocumentReference docRef = firestore.collection("contracts").document(contractId);
			var snapshot = docRef.get().get();

			if (!snapshot.exists()) {
				log.warn("No contract found with ID {}", contractId);
				throw new IllegalArgumentException("Contract not found");
			}
			verifyOwnership(snapshot);

			String id = snapshot.getId();
			String contractUserId = snapshot.getString("userId");
			String contractText = snapshot.getString("contractText");
			ContractStatus status = ContractStatus.valueOf(snapshot.getString("status"));

			List<String> extractedClauses = snapshot.contains("extractedClauses")
					? (List<String>) snapshot.get("extractedClauses") : null;

			String soliditySource = snapshot.getString("soliditySource");
			String binary = snapshot.getString("binary");
			String abi = snapshot.getString("abi");
			String deployedAddress = snapshot.getString("deployedAddress");

			return new DigitalizedContract(
					id,
					contractUserId,
					contractText,
					status,
					extractedClauses,
					soliditySource,
					binary,
					abi,
					deployedAddress
			);

		} catch (Exception e) {
			log.error("Failed to fetch contract status for {}", contractId, e);
			throw new RuntimeException("Failed to retrieve contract status", e);
		}
	}

	@Override
	public List<String> extractClauses(String contractId) {
		try {
			DocumentReference docRef = firestore.collection("contracts").document(contractId);
			var snapshot = docRef.get().get();
			if (!snapshot.exists()) {
				log.warn("No contract found with ID {}", contractId);
				throw new IllegalArgumentException("Contract not found");
			}
			verifyOwnership(snapshot);

			List<String> cached = (List<String>) snapshot.get("extractedClauses");
			if (cached != null && !cached.isEmpty()) {
				log.info("Using cached clauses for {}", contractId);
				return cached;
			}

			String text = snapshot.getString("contractText");
			List<String> clauses = aiService.extractClauses(text);
			docRef.update(Map.of("extractedClauses", clauses, "status", ContractStatus.CLAUSES_EXTRACTED.name()));

			log.info("Extracted {} clauses", clauses.size());
			return clauses;

		} catch (Exception e) {
			log.error("Error extracting clauses for contract {}", contractId, e);
			throw new RuntimeException("Clause extraction failed", e);
		}
	}

	@Override
	public String generateSolidity(String contractId) {
		try {
			DocumentReference docRef = firestore.collection("contracts").document(contractId);
			var snapshot = docRef.get().get();

			if (!snapshot.exists()) {
				log.warn("No contract found with ID {}", contractId);
				throw new IllegalArgumentException("Contract not found");
			}
			verifyOwnership(snapshot);

			List<String> clauses = (List<String>) snapshot.get("extractedClauses");
			if (clauses == null || clauses.isEmpty()) {
				throw new IllegalStateException("No extracted clauses found for contract: " + contractId);
			}

			String soliditySource = aiService.generateSolidityContract(clauses);

			// 🛠 Compile using your existing compiler
			CompilationResultDTO result = solidityCompiler.compile(soliditySource);
			String bin = result.getBin();
			String abi = result.getAbi();

			// 🧾 Store all in Firestore
			docRef.update(Map.of(
					"soliditySource", soliditySource,
					"binary", bin,
					"abi", abi,
					"status", ContractStatus.SOLIDITY_GENERATED.name()
			));

			log.info("Generated & compiled Solidity for contract {}:\n{}", contractId, soliditySource);
			return soliditySource;

		} catch (Exception e) {
			log.error("Error generating Solidity for contract {}", contractId, e);
			throw new RuntimeException("Failed to generate Solidity", e);
		}
	}

	@Override
	public String deployContractWithParams(String contractId, List<Object> constructorParams) {
		try {
			ContractDeploymentContext context = prepareDeploymentContext(contractId, constructorParams);

			String contractAddress = ethereumService.deployCompiledContract(
					context.ethContext().contractBinary(),
					context.ethContext().encodedConstructor()
			);

			context.contractRef().update(
					"status", ContractStatus.DEPLOYED.name(),
					"deployedAddress", contractAddress
			);

			log.info("User {} deployed contract {} at address {}", context.userId(), contractId, contractAddress);
			return contractAddress;

		} catch (Exception e) {
			log.error("Deployment failed for contract {}: {}", contractId, e.getMessage(), e);
			throw new RuntimeException("Deployment failed", e);
		}
	}

	@Override
	public BigInteger estimateGasForDeployment(String contractId, List<Object> constructorParams) {
		try {
			ContractDeploymentContext context = prepareDeploymentContext(contractId, constructorParams);

			BigInteger estimatedGas = ethereumService.estimateGasForDeployment(
					context.ethContext().contractBinary(),
					context.ethContext().encodedConstructor()
			);

			log.info("Estimated gas for contract {}: {}", contractId, estimatedGas);
			return estimatedGas;

		} catch (Exception e) {
			log.error("Gas estimation failed for contract {}: {}", contractId, e.getMessage(), e);
			throw new RuntimeException("Gas estimation failed", e);
		}
	}

	private void verifyOwnership(DocumentSnapshot snapshot) {
		String contractUserId = snapshot.getString("userId");
		String currentUserId = authService.getCurrentUserId();

		if (contractUserId == null || !contractUserId.equals(currentUserId)) {
			throw new SecurityException("You are not authorized to access this contract.");
		}
	}

	private ContractDeploymentContext prepareDeploymentContext(String contractId, List<Object> constructorParams) throws Exception {
		String userId = authService.getCurrentUserId();
		if (userId == null) {
			throw new IllegalStateException("User not authenticated");
		}

		DocumentReference contractRef = firestore.collection("contracts").document(contractId);
		DocumentSnapshot snapshot = contractRef.get().get();
		if (!snapshot.exists()) {
			log.warn("No contract found with ID {}", contractId);
			throw new IllegalArgumentException("Contract not found");
		}
		verifyOwnership(snapshot);

		String contractBinary = snapshot.getString("binary");
		if (contractBinary == null || contractBinary.isEmpty()) {
			throw new IllegalStateException("Contract binary not found or empty in Firestore");
		}

		// 🔁 Moved Web3 constructor encoding logic to EthereumService
		EthereumContractContext ethContext = ethereumService.buildDeploymentContext(contractBinary, constructorParams);

		return new ContractDeploymentContext(userId, ethContext, contractRef);
	}

}