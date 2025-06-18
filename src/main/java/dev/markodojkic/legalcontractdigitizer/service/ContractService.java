package dev.markodojkic.legalcontractdigitizer.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;

public class ContractService {
	private final Firestore db;

	public ContractService() {
		db = FirestoreClient.getFirestore();
	}

	public void saveContract(String contractId, String contractText) {
		DocumentReference contractRef = db.collection("contracts").document(contractId);
		contractRef.set(new DigitalizedContract(contractId, contractText));
	}

	private record DigitalizedContract (String id, String text) {}
}
