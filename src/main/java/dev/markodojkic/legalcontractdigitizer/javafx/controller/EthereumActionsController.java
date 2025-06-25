package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import dev.markodojkic.legalcontractdigitizer.dto.GasEstimateResponseDTO;
import dev.markodojkic.legalcontractdigitizer.enums_records.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.enums_records.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class EthereumActionsController implements WindowAwareController {
	@Getter
	@Setter
	private JavaFXWindowController windowController;

	@FXML private Label contractIdLabel, gasResultLabel, confirmedResultLabel, balanceLabel;
	@FXML private Button estimateGasBtn, deployContractBtn, checkConfirmedBtn, viewOnBlockchainBtn, getReceiptBtn, payBtn, completeBtn, terminateBtn, viewPartiesBtn;
	@FXML private TextField transactionHashField, valueField;
	@FXML private TextArea receiptTextArea;
	private Timeline autoRefreshTimeline;

	@Setter
	private DigitalizedContract contract;

	private final WindowLauncher windowLauncher;
	private final ApplicationContext applicationContext;

	private final String baseUrl;
	private final String etherscanUrl;

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
		reset();
		contractIdLabel.setText("Contract ID: " + contract.id());
		updateButtonsByStatus(contract.status());
		refreshBalance();
		startAutoRefreshBalance();

		estimateGasBtn.setOnAction(e -> estimateGas());
		deployContractBtn.setOnAction(e -> deployContract());
		checkConfirmedBtn.setOnAction(e -> checkConfirmation());
		viewOnBlockchainBtn.setOnAction(e -> openEtherscan());
		getReceiptBtn.setOnAction(e -> getTransactionReceipt());
		transactionHashField.textProperty().addListener((_, _, newValue) -> getReceiptBtn.setDisable(newValue.isEmpty()));
		payBtn.setOnAction(e -> {
			try {
				String ethValue = valueField.getText();
				BigInteger valueWei = (ethValue != null && !ethValue.isBlank())
						? new BigDecimal(ethValue).multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L)).toBigIntegerExact()
						: BigInteger.ZERO;
				invokeFunction("payUpfront", List.of(), valueWei);
			} catch (NumberFormatException | ArithmeticException ex) {
				log.error("Invalid ETH value entered: {}", valueField.getText(), ex);
				Platform.runLater(() -> confirmedResultLabel.setText("Invalid ETH value"));
			}
		});

		completeBtn.setOnAction(e -> invokeFunction("markProjectAsCompleted", List.of(), BigInteger.ZERO));
		terminateBtn.setOnAction(e -> invokeFunction("terminateContract", List.of(), BigInteger.ZERO));
		viewPartiesBtn.setOnAction(e -> openPartiesBalanceWindow());
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

				payBtn.setDisable(false);
				completeBtn.setDisable(false);
				terminateBtn.setDisable(false);
				viewPartiesBtn.setDisable(false);

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

		payBtn.setDisable(true);
		completeBtn.setDisable(false); //TODO: Checks pending
		terminateBtn.setDisable(false); //TODO: Checks pending
		viewPartiesBtn.setDisable(true);

		balanceLabel.setText("Balance: —");
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
					1024,
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

	private void refreshBalance() {
		try {
			String url = baseUrl + "/" + contract.deployedAddress() + "/balance";
			ResponseEntity<String> response = HttpClientUtil.get(url, null, String.class);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				String balance = response.getBody();
				Platform.runLater(() -> balanceLabel.setText("Balance: " + Convert.fromWei(new BigDecimal(balance), Convert.Unit.ETHER) + " ETH"));
			} else {
				Platform.runLater(() -> balanceLabel.setText("Failed to fetch balance"));
			}
		} catch (Exception e) {
			log.error("Error fetching balance", e);
			Platform.runLater(() -> balanceLabel.setText("Error fetching balance"));
		}
	}

	private void invokeFunction(String fn, List<Object> params, BigInteger valueWei) {
		try {
			String url = baseUrl + "/" + contract.deployedAddress() + "/invoke";
			Map<String, Object> body = Map.of(
					"abi", contract.abi(),
					"functionName", fn,
					"params", params,
					"valueWei", valueWei
			);

			ResponseEntity<String> response = HttpClientUtil.post(url, null, body, String.class);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				String txHash = response.getBody();
				Platform.runLater(() -> {
					confirmedResultLabel.setText("Tx: " + txHash);
					refreshBalance();
				});
			} else {
				Platform.runLater(() -> confirmedResultLabel.setText("Transaction failed"));
			}
		} catch (Exception e) {
			log.error("Error invoking function " + fn, e);
			Platform.runLater(() -> confirmedResultLabel.setText("Error invoking function"));
		}
	}

	private void startAutoRefreshBalance() {
		if (autoRefreshTimeline != null) {
			autoRefreshTimeline.stop();
		}

		autoRefreshTimeline = new Timeline(
				new KeyFrame(Duration.seconds(30), event -> {
					refreshBalance();
				})
		);
		autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
		autoRefreshTimeline.play();
	}

	private void openPartiesBalanceWindow() {
		try {
			ContractPartiesBalancesController controller = applicationContext.getBean(ContractPartiesBalancesController.class);
			controller.setContract(contract);
			windowLauncher.launchWindow(
					new Stage(),
					"Contract Parties & Balances - " + contract.id(),
					1024,
					768,
					"/layout/contract_parties_balances.fxml",
					Objects.requireNonNull(getClass().getResource("/static/style/contract_parties_balances.css")).toExternalForm(),
					controller
			);
		} catch (Exception e) {
			log.error("Failed to open parties balance window", e);
		}
	}
}
