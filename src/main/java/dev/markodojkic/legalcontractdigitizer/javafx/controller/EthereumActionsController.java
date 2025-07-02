package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.markodojkic.legalcontractdigitizer.model.*;
import dev.markodojkic.legalcontractdigitizer.exception.InvalidFunctionCallException;
import dev.markodojkic.legalcontractdigitizer.exception.WalletNotFoundException;
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
import javafx.util.Duration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpResponseException;
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

@Component
@Slf4j
public class EthereumActionsController extends WindowAwareController {
	@FXML private Label contractIdLabel, gasResultLabel, balanceLabel;
	@FXML private Button estimateGasBtn, deployContractBtn, checkConfirmedBtn, viewOnBlockchainBtn, getReceiptBtn;
	@FXML private TextField transactionHashField;
	@FXML private TextArea receiptTextArea;
	@FXML private FlowPane additionalActionButtonPane;
	private Timeline autoRefreshTimeline;

	@Setter
	private DigitalizedContract contract;

	private final HttpClientUtil httpClientUtil;
	private final String baseUrl;
	private final String etherscanUrl;

	@Autowired
	public EthereumActionsController(@Value("${server.port}") Integer serverPort,
									 @Value("${ethereum.etherscan.url}") String etherscanUrl,
	                                 WindowLauncher windowLauncher,
	                                 ApplicationContext applicationContext,
	                                 HttpClientUtil httpClientUtil){
		super(windowLauncher, applicationContext);
		this.baseUrl = String.format("http://localhost:%s/api/v1/ethereum", serverPort);
		this.etherscanUrl = etherscanUrl;
		this.httpClientUtil = httpClientUtil;
	}

	@FXML
	public void initialize() {
		// Initialize UI based on contract
		reset();
		contractIdLabel.setText("Contract ID: " + contract.id());
		updateButtonsByStatus(contract.status());
		if(contract.status().compareTo(ContractStatus.CONFIRMED) > 0){
			refreshBalance();
			startAutoRefreshBalance();
		}

		estimateGasBtn.setOnAction(e -> estimateGas());
		deployContractBtn.setOnAction(e -> deployContract());
		checkConfirmedBtn.setOnAction(e -> checkConfirmation());
		viewOnBlockchainBtn.setOnAction(e -> viewContractOnBlockchain());
		getReceiptBtn.setOnAction(e -> getTransactionReceipt());
		transactionHashField.textProperty().addListener((_, _, newValue) -> getReceiptBtn.setDisable(newValue.isEmpty()));
	}

	private void updateButtonsByStatus(ContractStatus status) {
		gasResultLabel.setText("");
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
			Pair<String, List<Object>> abiResult = promptForAbiParams(contract.abi(), "constructor", true);

			ResponseEntity<GasEstimateResponseDTO> response = httpClientUtil.post(
					baseUrl + "/estimate-gas",
					null,
					new DeploymentRequestDTO(contract.id(), abiResult.getLeft(), abiResult.getRight()),
					GasEstimateResponseDTO.class
			);

			if(response.getBody() == null) throw new NoHttpResponseException("Gas estimation failed with no response");
			else if (response.getStatusCode().is2xxSuccessful())
				Platform.runLater(() -> {
					gasResultLabel.setText("Gas Limit: " + String.format("%,d", response.getBody().gasLimit()) +
											"\nGas Price: " + Convert.fromWei(new BigDecimal(response.getBody().gasPriceWei()), Convert.Unit.GWEI)
											.setScale(6, RoundingMode.HALF_UP).toPlainString() + " Gwei" +
											"\nEstimated Cost: " + Convert.fromWei(new BigDecimal(response.getBody().gasPriceWei().multiply(response.getBody().gasLimit())), Convert.Unit.ETHER)
											.setScale(6, RoundingMode.HALF_UP).toPlainString() + " Sepolia ETH");
					windowLauncher.launchSuccessSpecialWindow("Gas estimation completed successfully for contract: " + contract.id());
				});
			else throw new HttpResponseException(response.getStatusCode().value(), response.getBody().message());
		} catch (Exception e) {
			log.error(e.getLocalizedMessage());
			windowLauncher.launchErrorSpecialWindow("Error occurred while estimating gas required to deploy a contract:\n" + e.getLocalizedMessage());
		}
	}

	private void deployContract() {
		try {
			Pair<String, List<Object>> abiResult = promptForAbiParams(contract.abi(), "constructor", true);

			ResponseEntity<String> response = httpClientUtil.post(
					baseUrl + "/deploy-contract",
					null,
					new DeploymentRequestDTO(contract.id(), abiResult.getLeft(), abiResult.getRight()),
					String.class
			);

			if(response.getBody() == null) throw new NoHttpResponseException("Contract deployment failed with no response");
			else if (response.getStatusCode().is2xxSuccessful())
				Platform.runLater(() -> {
					reset();
					updateButtonsByStatus(ContractStatus.DEPLOYED);
					windowLauncher.launchSuccessSpecialWindow(response.getBody());
				});
			else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
		} catch (Exception e) {
			log.error(e.getLocalizedMessage());
			windowLauncher.launchErrorSpecialWindow("Error occurred while deploying contract:\n" + e.getLocalizedMessage());
		}
	}

	private void checkConfirmation() {
		try {
			ResponseEntity<String> response = httpClientUtil.get(
					baseUrl + "/" + contract.deployedAddress() + "/confirmed",
					null,
					String.class
			);

			if(response.getBody() == null) throw new NoHttpResponseException("Deployed contract confirmation check failed with no response");
			else if (response.getStatusCode().is2xxSuccessful())
				Platform.runLater(() -> {
					if (response.getBody().equals("true")) {
						reset();
						updateButtonsByStatus(ContractStatus.CONFIRMED);
						windowLauncher.launchSuccessSpecialWindow("Smart contract deployed at address \"" + contract.deployedAddress() + "\" has been confirmed on Blockchain");
					} else
						windowLauncher.launchSuccessSpecialWindow("Smart contract deployed at address \"" + contract.deployedAddress() + "\" have not yet been confirmed on Blockchain");
				});
			else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
		} catch (Exception e) {
			log.error(e.getLocalizedMessage());
			windowLauncher.launchErrorSpecialWindow("Error occurred while checking deployed contract confirmation:\n" + e.getLocalizedMessage());
		}
	}

	private void viewContractOnBlockchain() {
		String address = contract.deployedAddress();
		if (address != null && !address.isBlank()) {
			windowLauncher.launchWebViewWindow(
					"Smart Contract view on Blockchain - " + contract.id(),
					1024,
					1024,
					String.format("%s/address/%s", etherscanUrl, address)
			);
		} else windowLauncher.launchWarnSpecialWindow("Cannot view contract on Blockchain cause no ethereum address was provided");
	}

	private void getTransactionReceipt() {
		try {
			ResponseEntity<String> response = httpClientUtil.get(
					baseUrl + "/transaction/" + transactionHashField.getText() +"/receipt",
					null,
					String.class
			);

			if(response.getBody() == null) throw new NoHttpResponseException("Action to get transaction receipt failed with no response");
			else if (response.getStatusCode().is2xxSuccessful())Platform.runLater(() -> receiptTextArea.setText(response.getBody()));
			else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
		} catch (Exception e) {
			log.error(e.getLocalizedMessage());
			windowLauncher.launchErrorSpecialWindow("Error occurred while getting transaction receipt:\n" + e.getLocalizedMessage());
		}
	}

	private Pair<String, List<Object>> promptForAbiParams(String abiJson, String targetNameOrType, boolean isConstructor) throws IllegalStateException {
		try {
			ConstructorInputController controller = applicationContext.getBean(ConstructorInputController.class);

			ResponseEntity<List<WalletInfo>> response = httpClientUtil.get(
					baseUrl + "/getAvailableWallets", null, new TypeToken<List<WalletInfo>>() {}.getType()
			);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) controller.setWalletInfos(response.getBody());
			else if (isConstructor) throw new WalletNotFoundException("No ethereum wallets found.");

			ConstructorInputController inputController = windowLauncher.launchWindow(
					isConstructor ? "Constructor Parameters" : "Function Parameters",
					500,
					500,
					"/layout/constructor_input.fxml",
					Objects.requireNonNull(getClass().getResource("/static/style/constructor_input.css")).toExternalForm(),
					controller
			);
			inputController.loadParamInputs(abiJson, targetNameOrType, isConstructor);

			List<Object> params = inputController.getParams();
			if (params == null) throw new IllegalStateException("Parameters were not provided or invalid.");
			return Pair.of(params.removeFirst().toString(), params);
		} catch (Exception e) {
			log.error("Error occurred while creating params", e);
			throw new IllegalStateException(e.getLocalizedMessage());
		}
	}

	private void refreshBalance() {
		try {
			ResponseEntity<String> response = httpClientUtil.get(baseUrl + "/" + contract.deployedAddress() + "/balance", null, String.class);

			if(response.getBody() == null) throw new NoHttpResponseException("Smart contract current balance retrieval failed with no response");
			else if (response.getStatusCode().is2xxSuccessful())
				Platform.runLater(() -> balanceLabel.setText("Balance \uD83E\uDE99: " + response.getBody() + " Sepolia ETH"));
			else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
		} catch (Exception e) {
			log.error(e.getLocalizedMessage());
			windowLauncher.launchErrorSpecialWindow("Error occurred while smart contract current balance retrieval:\n" + e.getLocalizedMessage());
		}
	}

	private void invokeFunction(String fn, String callerWallet, List<Object> params, BigInteger valueWei) {
		try {
			ResponseEntity<String> response = httpClientUtil.post(baseUrl + "/" + contract.deployedAddress() + "/invoke", null,
					new ContractFunctionRequestDTO(fn, params, valueWei.toString(), callerWallet), String.class);

			if(response.getBody() == null) throw new NoHttpResponseException("Deployed contract confirmation check failed with no response");
			else if (response.getStatusCode().is2xxSuccessful())
				Platform.runLater(() -> {
					transactionHashField.setText(response.getBody());
					refreshBalance();
					windowLauncher.launchSuccessSpecialWindow("Action successfully invoked upon smart contract. Transaction hash field has been populated corresponding transaction hash id");
				});
			else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
		} catch (Exception e) {
			log.error(e.getLocalizedMessage());
			throw new InvalidFunctionCallException("Error occurred while invoking action upon smart contract:\n" + e.getLocalizedMessage(), e);
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
			List<String> functionGetters = new ArrayList<>();
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

							functionGetters.add(obj.get("name").getAsString());
						}
					}
				}
			}

			if (!functionGetters.isEmpty()) {
				ResponseEntity<ContractPartiesAddressDataResponseDTO> response = httpClientUtil.post(
						baseUrl + "/resolveContractPartiesAddressData",
						null,
						new ContractPartiesAddressDataRequestDTO(contract.deployedAddress(), functionGetters),
						ContractPartiesAddressDataResponseDTO.class
				);

				if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
					Map<String, String> resultMap = response.getBody().resolvedAddresses();

					VBox getterBox = new VBox(5);
					getterBox.setPadding(new Insets(10));
					getterBox.getChildren().add(new Label("Involved parties (• identification → address):"));

					for (var entry : resultMap.entrySet()) {
						getterBox.getChildren().add(new Label("• " + entry.getKey() + " → " + entry.getValue()));
					}

					Platform.runLater(() -> additionalActionButtonPane.getChildren().add(getterBox));
				} else windowLauncher.launchWarnSpecialWindow("Confirm contract UI generation cannot get involved parties data");
			}

		} catch (Exception e) {
			log.error("Confirm contract UI generation failure", e);
			windowLauncher.launchErrorSpecialWindow("Confirm contract UI generation failure\n" + e.getLocalizedMessage());
		}
	}

	private @NotNull Button generateButton(String fnName, String stateMutability) {
		Button fnButton = new Button(fnName);
		fnButton.setOnAction(_ -> {
			try {
				// Prompt user for all params including caller wallet as first param
				Pair<String, List<Object>> abiResult = promptForAbiParams(contract.abi(), fnName, false);
				if (abiResult.getLeft().isEmpty() || abiResult.getRight().isEmpty()) {
					windowLauncher.launchWarnSpecialWindow("Invoking action failure:\nNo parameters provided");
					return;
				}

				// Validate caller wallet address - must be first param
				if (!abiResult.getLeft().matches("^0x[0-9a-fA-F]{40}$")) {
					windowLauncher.launchWarnSpecialWindow("Invoking action failure:\nMissing or invalid caller wallet address");
					return;
				}

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
							throw new IllegalArgumentException("Invoking action failure:Invalid ETH amount");
						}
						weiAmount.set(Convert.toWei(eth, Convert.Unit.ETHER).toBigIntegerExact());
					}, () -> {
						throw new IllegalArgumentException("Invoking action failure: ETH payment required for payable function");
					});

					valueWei = weiAmount.get();
				}

				invokeFunction(fnName, abiResult.getLeft(), abiResult.getRight(), valueWei);
			} catch (IllegalArgumentException illegalArgumentException){
				log.warn(illegalArgumentException.getLocalizedMessage());
				windowLauncher.launchWarnSpecialWindow("Invoking action failure:\n" + illegalArgumentException.getLocalizedMessage());

			} catch (Exception e) {
				log.error(e.getLocalizedMessage());
				windowLauncher.launchErrorSpecialWindow(e.getLocalizedMessage());
			}
		});
		return fnButton;
	}

}
