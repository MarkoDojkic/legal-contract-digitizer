package dev.markodojkic.legalcontractdigitizer.util;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import dev.markodojkic.legalcontractdigitizer.enums.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.service.EthereumService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ContractStatusWorker {

	private final Firestore firestore = FirestoreClient.getFirestore();
	private final EthereumService ethereumService;

	@Scheduled(fixedRate = 30000)
	public void checkDeployed() {
		try {
			QuerySnapshot snaps = firestore.collection("contracts")
					.whereEqualTo("status", ContractStatus.DEPLOYED.name())
					.get().get();
			List<QueryDocumentSnapshot> docs = snaps.getDocuments();

			for (var d : docs) {
				String id = d.getId();
				String addr = d.getString("deployedAddress");
				if (addr == null) continue;

				boolean confirmed = ethereumService.isContractConfirmed(addr);
				if (confirmed) {
					firestore.collection("contracts").document(id)
							.update("status", ContractStatus.CONFIRMED.name());
					log.info("Contract {} CONFIRMED", id);
				}
			}
		} catch (Exception e) {
			log.error("Worker error", e);
		}
	}
}