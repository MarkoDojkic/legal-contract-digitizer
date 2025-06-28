package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.markodojkic.legalcontractdigitizer.dto.ContractAddressGettersRequestDTO;
import dev.markodojkic.legalcontractdigitizer.dto.ContractAddressGettersResponseDTO;
import dev.markodojkic.legalcontractdigitizer.dto.GasEstimateResponseDTO;
import dev.markodojkic.legalcontractdigitizer.dto.WalletInfo;
import dev.markodojkic.legalcontractdigitizer.enums_records.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.enums_records.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class EthereumActionsController implements WindowAwareController {
	@Setter
	private JavaFXWindowController windowController;

	@FXML private Label contractIdLabel, gasResultLabel, confirmedResultLabel, balanceLabel;
	@FXML private Button estimateGasBtn, deployContractBtn, checkConfirmedBtn, viewOnBlockchainBtn, getReceiptBtn;
	@FXML private TextField transactionHashField;
	@FXML private TextArea receiptTextArea;
	@FXML private FlowPane additionalActionButtonPane;
	private Timeline autoRefreshTimeline;

	@Setter
	private DigitalizedContract contract;

	private final WindowLauncher windowLauncher;
	private final ApplicationContext applicationContext;
	private final HttpClientUtil httpClientUtil;
	private final String baseUrl;
	private final String etherscanUrl;

	@Autowired
	public EthereumActionsController(@Value("${server.port}") Integer serverPort,
									 @Value("${ethereum.etherscan.url}") String etherscanUrl,
	                                 WindowLauncher windowLauncher, HttpClientUtil httpClientUtil,
	                                 ApplicationContext applicationContext){
		this.baseUrl = String.format("http://localhost:%s/api/v1/ethereum", serverPort);
		this.etherscanUrl = etherscanUrl;
		this.httpClientUtil = httpClientUtil;
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
		viewOnBlockchainBtn.setOnAction(e -> viewContractOnBlockchain());
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
			case CONFIRMED, TERMINATED -> {
				estimateGasBtn.setDisable(true);
                viewOnBlockchainBtn.setDisable(false);
				addConfirmedContractUI();
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
		balanceLabel.setText("Balance: —");
	}

	private void estimateGas() {
		try {
			String url = baseUrl + "/estimate-gas";

			var constructorParams = promptForAbiParams(contract.abi(), "constructor", true);

			Map<String, Object> bodyMap = Map.of(
					"contractId", contract.id(),
					"deployerWalletAddress", constructorParams.removeFirst().toString(),
					"constructorParams", constructorParams
			);

			ResponseEntity<GasEstimateResponseDTO> response = httpClientUtil.post(
					url,
					null,
					bodyMap,
					GasEstimateResponseDTO.class
			);

			GasEstimateResponseDTO gasResp = response.getBody();
			if (response.getStatusCode().is2xxSuccessful()) {
				Platform.runLater(() -> gasResultLabel.setText(
						"Gas Limit: " + gasResp.getGasLimit() +
								"\nGas Price: " + Convert.fromWei(new BigDecimal(gasResp.getGasPrice()), Convert.Unit.GWEI)
								.setScale(2, RoundingMode.HALF_UP).toPlainString() + " Gwei" +
								"\nEstimated Cost: " + Convert.fromWei(new BigDecimal(gasResp.getTotalCost()), Convert.Unit.ETHER)
								.setScale(6, RoundingMode.HALF_UP).toPlainString() + " Sepolia ETH"
				));
			} else {
				Platform.runLater(() -> gasResultLabel.setText("Failed to estimate gas: " + gasResp.getMessage()));
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

			var constructorParams = promptForAbiParams(contract.abi(), "constructor", true);

			Map<String, Object> bodyMap = Map.of(
					"contractId", contract.id(),
					"deployerWalletAddress", constructorParams.removeFirst().toString(),
					"constructorParams", constructorParams
			);

			ResponseEntity<Void> response = httpClientUtil.post(
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

			ResponseEntity<Boolean> response = httpClientUtil.get(
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

	private void viewContractOnBlockchain() {
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

			ResponseEntity<String> response = httpClientUtil.get(
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

	private List<Object> promptForAbiParams(String abiJson, String targetNameOrType, boolean isConstructor) {
		try {
			Stage paramStage = new Stage();
			ConstructorInputController controller = applicationContext.getBean(ConstructorInputController.class);

			// Always fetch wallets because the caller wallet is always needed
			String url = baseUrl + "/getAvailableWallets";
			ResponseEntity<List<WalletInfo>> response = httpClientUtil.get(
					url, null, new TypeToken<List<WalletInfo>>() {}.getType()
			);
			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				controller.setWalletInfos(response.getBody());
			} else if (isConstructor) {
				throw new IllegalStateException("No ethereum wallets found.");
			}

			ConstructorInputController inputController = windowLauncher.launchWindow(
					paramStage,
					isConstructor ? "Constructor Parameters" : "Function Parameters",
					500,
					500,
					"/layout/constructor_input.fxml",
					Objects.requireNonNull(getClass().getResource("/static/style/constructor_input.css")).toExternalForm(),
					controller
			);
			inputController.loadParamInputs(abiJson, targetNameOrType, isConstructor);
			paramStage.showAndWait();

			List<Object> params = inputController.getParams();
			if (params == null) throw new IllegalStateException("Parameters were not provided or invalid.");
			return params;
		} catch (Exception e) {
			log.error("Error occurred while creating params", e);
			throw new IllegalStateException(e.getMessage());
		}
	}

	private void refreshBalance() {
		try {
			String url = baseUrl + "/" + contract.deployedAddress() + "/balance";
			ResponseEntity<String> response = httpClientUtil.get(url, null, String.class);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				String balance = response.getBody();
				Platform.runLater(() -> balanceLabel.setText("Balance: " + Convert.fromWei(new BigDecimal(balance), Convert.Unit.ETHER) + " Sepolia ETH"));
			} else {
				Platform.runLater(() -> balanceLabel.setText("Failed to fetch balance"));
			}
		} catch (Exception e) {
			log.error("Error fetching balance", e);
			Platform.runLater(() -> balanceLabel.setText("Error fetching balance"));
		}
	}

	private void invokeFunction(String fn, String callerWallet, List<Object> params, BigInteger valueWei) {
		try {
			String url = baseUrl + "/" + contract.deployedAddress() + "/invoke";
			Map<String, Object> body = Map.of(
					"functionName", fn,
					"params", params,
					"valueWei", valueWei.toString(),  // Send as string to avoid JSON issues with BigInteger
					"requestedByWalletAddress", callerWallet
			);

			ResponseEntity<String> response = httpClientUtil.post(url, null, body, String.class);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				String txHash = response.getBody();
				Platform.runLater(() -> {
					confirmedResultLabel.setText("Function executed successfully!");
					transactionHashField.setText(txHash);
					refreshBalance();
				});
			} else {
				Platform.runLater(() -> confirmedResultLabel.setText("Transaction failed"));
			}
		} catch (Exception e) {
			Platform.runLater(() -> confirmedResultLabel.setText("Error invoking function"));
		}
	}

	private void startAutoRefreshBalance() {
		if (autoRefreshTimeline != null) {
			autoRefreshTimeline.stop();
		}

		autoRefreshTimeline = new Timeline(
				new KeyFrame(Duration.seconds(30), _ -> refreshBalance())
		);
		autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
		autoRefreshTimeline.play();
	}

	private void addConfirmedContractUI() {
		if (contract.abi() == null) return;

		additionalActionButtonPane.getChildren().clear(); // Clear existing buttons

		try {
			// IF STATUS IS TERMINATED, do not add any buttons
			if (contract.status() == ContractStatus.TERMINATED) Platform.runLater(() -> additionalActionButtonPane.getChildren().add(new Label("Contract is terminated, no actions available.")));

			JsonArray abiArray = JsonParser.parseString(contract.abi()).getAsJsonArray();
			List<String> addressGetters = new ArrayList<>();
			for (JsonElement el : abiArray) {
				JsonObject obj = el.getAsJsonObject();

				if (!"function".equals(obj.get("type").getAsString()) || !obj.has("name")) continue;

				String fnName = obj.get("name").getAsString();
				String stateMutability = obj.get("stateMutability").getAsString();

				// Only non-constant functions get buttons
				if (!"view".equals(stateMutability) && !"pure".equals(stateMutability)) {
					if(contract.status() != ContractStatus.TERMINATED) additionalActionButtonPane.getChildren().add(generateButton(fnName, stateMutability));
				} else {
					if ("function".equals(obj.get("type").getAsString())
							&& obj.has("name")
							&& obj.has("outputs")) {

						JsonArray outputs = obj.get("outputs").getAsJsonArray();
						if (outputs.size() == 1 && "address".equals(outputs.get(0).getAsJsonObject().get("type").getAsString())
								&& obj.get("inputs").getAsJsonArray().isEmpty()) {

							addressGetters.add(obj.get("name").getAsString());
						}
					}
				}
			}

			if (!addressGetters.isEmpty()) {
				var req = new ContractAddressGettersRequestDTO(contract.deployedAddress(), addressGetters);

				ResponseEntity<ContractAddressGettersResponseDTO> response = httpClientUtil.post(
						baseUrl + "/resolve-addresses",
						null,
						req,
						ContractAddressGettersResponseDTO.class
				);

				if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
					Map<String, String> resultMap = response.getBody().getResolvedAddresses();

					VBox getterBox = new VBox(5);
					getterBox.setPadding(new Insets(10));
					getterBox.getChildren().add(new Label("Address Getters:"));

					for (var entry : resultMap.entrySet()) {
						getterBox.getChildren().add(new Label("• " + entry.getKey() + " → " + entry.getValue()));
					}

					Platform.runLater(() -> additionalActionButtonPane.getChildren().add(getterBox));
				}
			}

		} catch (Exception e) {
			log.error("Failed to parse ABI for dynamic function buttons", e);
		}
	}

	private @NotNull Button generateButton(String fnName, String stateMutability) {
		Button fnButton = new Button(fnName);
		fnButton.setOnAction(e -> {
			try {
				// Prompt user for all params including caller wallet as first param
				List<Object> params = promptForAbiParams(contract.abi(), fnName, false);
				if (params == null) {
					Platform.runLater(() -> confirmedResultLabel.setText("No parameters provided"));
					return;
				}

				// Validate caller wallet address - must be first param
				if (params.isEmpty()) {
					Platform.runLater(() -> confirmedResultLabel.setText("Missing caller wallet address"));
					return;
				}

				Object callerWalletObj = params.remove(0); // Remove caller wallet from params list
				if (!(callerWalletObj instanceof String)) {
					Platform.runLater(() -> confirmedResultLabel.setText("Invalid wallet address"));
					return;
				}
				String callerWallet = (String) callerWalletObj;

				BigInteger valueWei = BigInteger.ZERO;

				// Prompt for ETH amount only if function is payable
				if ("payable".equalsIgnoreCase(stateMutability)) {
					TextInputDialog dialog = new TextInputDialog();
					dialog.setTitle("Enter Ether Payment");
					dialog.setHeaderText("Function is payable");
					dialog.setContentText("Amount in Sepolia ETH:");

					AtomicReference<BigInteger> weiAmount = new AtomicReference<>(BigInteger.ZERO);
					dialog.showAndWait().ifPresentOrElse(eth -> {
						if (eth.isBlank() || !eth.matches("^[0-9]*\\.?[0-9]+$")) {
							throw new IllegalArgumentException("Invalid ETH amount");
						}
						weiAmount.set(Convert.toWei(eth, Convert.Unit.ETHER).toBigIntegerExact());
					}, () -> {
						throw new IllegalArgumentException("ETH payment required for payable function");
					});

					valueWei = weiAmount.get();
				}

				// Call backend invokeFunction with function name, caller wallet, params, and valueWei
				invokeFunction(fnName, callerWallet, params, valueWei);

			} catch (IllegalArgumentException ex) {
				Platform.runLater(() -> confirmedResultLabel.setText("Input error: " + ex.getMessage()));
			} catch (Exception ex) {
				Platform.runLater(() -> confirmedResultLabel.setText("Error invoking function"));
			}
		});
		return fnButton;
	}

}
