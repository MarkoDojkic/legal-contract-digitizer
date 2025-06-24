package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import dev.markodojkic.legalcontractdigitizer.dto.GasEstimateResponseDTO;
import dev.markodojkic.legalcontractdigitizer.enums_records.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.enums_records.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class EthereumActionsController implements WindowAwareController {
	@Getter
	@Setter
	private JavaFXWindowController windowController;

	@FXML private Label contractIdLabel;
	@FXML private Button estimateGasBtn;
	@FXML private Label gasResultLabel;

	@FXML private Button deployContractBtn;
	@FXML private Button checkConfirmedBtn;
	@FXML private Button viewOnBlockchainBtn;
	@FXML private Label confirmedResultLabel;

	@FXML private Button getReceiptBtn;
	@FXML private TextField transactionHashField;
	@FXML private TextArea receiptTextArea;

	@Setter
	private DigitalizedContract contract;

	@Value("${server.port}")
	private Integer serverPort;

	@Value("${ethereum.etherscan.url}")
	private String etherscanUrl;

	private String baseUrl;

	private final WindowLauncher windowLauncher;
	private final ApplicationContext applicationContext;

	public EthereumActionsController(@Value("${server.port}") Integer serverPort,
	                                 @Value("${ethereum.etherscan.url}") String etherscanUrl,
	                                 @Autowired WindowLauncher windowLauncher,
	                                 @Autowired ApplicationContext applicationContext){
		this.baseUrl = String.format("http://localhost:%s/api/v1/ethereum", serverPort);
		this.etherscanUrl = etherscanUrl;
		this.windowLauncher = windowLauncher;
		this.applicationContext = applicationContext;
	}

	@FXML
	public void initialize() {
		// Initialize UI based on contract
		Platform.runLater(() -> {
			reset();
			contractIdLabel.setText("Contract ID: " + contract.id());
			updateButtonsByStatus(contract.status());
		});

		estimateGasBtn.setOnAction(e -> estimateGas());
		deployContractBtn.setOnAction(e -> deployContract());
		checkConfirmedBtn.setOnAction(e -> checkConfirmation());
		viewOnBlockchainBtn.setOnAction(e -> openEtherscan());
		getReceiptBtn.setOnAction(e -> getTransactionReceipt());
		transactionHashField.textProperty().addListener((_, _, newValue) -> getReceiptBtn.setDisable(newValue.isEmpty()));
	}

	private void updateButtonsByStatus(ContractStatus status) {
		gasResultLabel.setText("");
		confirmedResultLabel.setText("");
		receiptTextArea.clear();

		switch (status) {
			case SOLIDITY_GENERATED -> deployContractBtn.setDisable(false);
			case DEPLOYED -> checkConfirmedBtn.setDisable(false);
			case CONFIRMED -> {
				viewOnBlockchainBtn.setDisable(false);
				estimateGasBtn.setDisable(true);
			}
			default -> reset();
		}
	}


	private void reset() {
		deployContractBtn.setDisable(true);
		checkConfirmedBtn.setDisable(true);
		viewOnBlockchainBtn.setDisable(true);
		estimateGasBtn.setDisable(false);
		getReceiptBtn.setDisable(true);
	}

	private void estimateGas() {
		try {
			String url = baseUrl + "/estimate-gas";

			Map<String, Object> bodyMap = Map.of(
					"contractId", contract.id(),
					"constructorParams", promptForConstructorParams()
			);

			ResponseEntity<GasEstimateResponseDTO> response = HttpClientUtil.post(
					url,
					null,
					bodyMap,
					GasEstimateResponseDTO.class
			);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				GasEstimateResponseDTO gasResp = response.getBody();
				Platform.runLater(() -> gasResultLabel.setText("Estimated Gas: " + gasResp.getGas() + " Wei"));
			} else {
				Platform.runLater(() -> gasResultLabel.setText("Failed to estimate gas"));
			}
		} catch (IllegalStateException ise) {
			log.warn("Gas estimation cancelled or invalid input: {}", ise.getMessage());
			Platform.runLater(() -> gasResultLabel.setText("Gas estimation cancelled or invalid input."));
		} catch (Exception e) {
			log.error("Error estimating gas", e);
			Platform.runLater(() -> gasResultLabel.setText("Error estimating gas."));
		}
	}

	private void deployContract() {
		try {
			String url = baseUrl + "/deploy-contract";

			Map<String, Object> bodyMap = Map.of(
					"contractId", contract.id(),
					"constructorParams", promptForConstructorParams()
			);

			ResponseEntity<Void> response = HttpClientUtil.post(
					url,
					null,
					bodyMap,
					Void.class
			);

			if (response.getStatusCode().is2xxSuccessful()) {
				log.info("Contract deployed");
				Platform.runLater(() -> {
					reset();
					updateButtonsByStatus(ContractStatus.DEPLOYED);
					confirmedResultLabel.setText("");
				});
			} else {
				String msg = "Deploy failed: HTTP " + response.getStatusCode();
				Platform.runLater(() -> confirmedResultLabel.setText(msg));
			}
		} catch (IllegalStateException ise) {
			log.warn("Deployment cancelled or invalid input: {}", ise.getMessage());
			Platform.runLater(() -> confirmedResultLabel.setText("Deployment cancelled or invalid input."));
		} catch (Exception e) {
			log.error("Error deploying contract", e);
			Platform.runLater(() -> confirmedResultLabel.setText("Error deploying contract."));
		}
	}


	private void checkConfirmation() {
		try {
			String url = baseUrl + "/" + contract.deployedAddress() + "/confirmed";

			ResponseEntity<Boolean> response = HttpClientUtil.get(
					url,
					null,
					Boolean.class
			);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				boolean confirmed = response.getBody();
				Platform.runLater(() -> {
					if (confirmed) {
						reset();
						confirmedResultLabel.setText("Contract confirmed!");
						updateButtonsByStatus(ContractStatus.CONFIRMED);
					} else {
						confirmedResultLabel.setText("Not confirmed yet");
					}
				});
			} else {
				Platform.runLater(() -> confirmedResultLabel.setText("Failed to check confirmation"));
			}
		} catch (Exception e) {
			log.error("Error checking confirmation", e);
			Platform.runLater(() -> confirmedResultLabel.setText("Error checking confirmation"));
		}
	}


	private void openEtherscan() {
		String address = contract.deployedAddress();
		if (address != null && !address.isBlank()) {
			windowLauncher.launchWebViewWindow(
					new Stage(),
					"Smart Contract view on Blockchain - " + contract.id(),
					1024,
					1280,
					String.format("%s/address/%s", etherscanUrl, address)
			);
		}
	}

	private void getTransactionReceipt() {
		try {
			String url = baseUrl + "/transaction/" + transactionHashField.getText() +"/receipt";

			ResponseEntity<String> response = HttpClientUtil.get(
					url,
					null,
					String.class
			);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				String respBody = response.getBody();
				Platform.runLater(() -> receiptTextArea.setText(respBody));
			} else {
				Platform.runLater(() -> receiptTextArea.setText("Failed to get transaction receipt"));
			}
		} catch (Exception e) {
			log.error("Error getting transaction receipt", e);
			Platform.runLater(() -> receiptTextArea.setText("Error getting transaction receipt"));
		}
	}

	private List<Object> promptForConstructorParams() {
		String abi = contract.abi();
		Stage constructorStage = new Stage();

		ConstructorInputController controller = applicationContext.getBean(ConstructorInputController.class);
		ConstructorInputController inputController = windowLauncher.launchWindow(
				constructorStage,
				"Constructor Parameters",
				500,
				500,
				"/layout/constructor_input.fxml",
				Objects.requireNonNull(getClass().getResource("/static/style/constructor_input.css")).toExternalForm(),
				controller
		);

		inputController.loadConstructorInputs(abi);
		constructorStage.showAndWait(); // blocks until user closes window

		List<Object> constructorParams = inputController.getConstructorParams();

		if (constructorParams == null) {
			throw new IllegalStateException("Constructor parameters were not provided or invalid.");
		}

		return constructorParams;
	}


}
