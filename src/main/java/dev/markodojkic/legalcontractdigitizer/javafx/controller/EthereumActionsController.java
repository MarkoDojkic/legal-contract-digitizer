package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.markodojkic.legalcontractdigitizer.model.*;
import dev.markodojkic.legalcontractdigitizer.exception.InvalidFunctionCallException;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
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
import org.springframework.http.HttpStatus;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class EthereumActionsController extends WindowAwareController {
	@FXML private Label contractIdLabel, gasResultLabel, balanceLabel;
	@FXML private Button estimateGasBtn, estimateGasHelpBtn, deployContractBtn, deployContractHelpBtn, checkConfirmedBtn, checkConfirmedHelpBtn, viewOnBlockchainBtn, viewOnBlockchainHelpBtn, getReceiptBtn, getReceiptHelpBtn;
	@Setter
	@FXML private Button mainRefreshBtn;
	@FXML private TextField transactionHashField;
	@FXML private TextArea receiptTextArea;
	@FXML private FlowPane additionalActionButtonPane;
	private Timeline autoRefreshTimeline;

	@Setter
	private DigitalizedContract contract;

	private final HttpClientUtil httpClientUtil;
	private final String baseUrl, etherscanUrl;

	@Autowired
	public EthereumActionsController(@Value("${server.port}") Integer serverPort, @Value("${ethereum.etherscan.url}") String etherscanUrl, WindowLauncher windowLauncher, ApplicationContext applicationContext, HttpClientUtil httpClientUtil){
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

		estimateGasBtn.setOnAction(_ -> estimateGas());
		estimateGasHelpBtn.setOnAction(_ -> windowLauncher.launchHelpSpecialWindow("Will open popup to input constructor arguments for previously generated Solidity contracts.\nUpon selecting appropriate wallets for Smart contract deployer and all parties involved, will receive estimated gas price and gas limit if deployed at current time"));
		deployContractBtn.setOnAction(_ -> deployContract());
		deployContractHelpBtn.setOnAction(_ -> windowLauncher.launchHelpSpecialWindow("Will open popup to input constructor arguments for previously generated Solidity contract.\nUpon selecting appropriate wallets for Smart contract deployer and all parties involved, contract will be deployed and deployment transaction hash will be received."));
		checkConfirmedBtn.setOnAction(_ -> checkConfirmation());
		checkConfirmedHelpBtn.setOnAction(_ -> windowLauncher.launchHelpSpecialWindow("Will check if previously deployed Smart contract deployment transaction is confirmed and it`s visible on Blockchain"));
		viewOnBlockchainBtn.setOnAction(_ -> viewContractOnBlockchain());
		viewOnBlockchainHelpBtn.setOnAction(_ -> windowLauncher.launchHelpSpecialWindow("Will open previously configured blockchain (or Sepolia testnet by default) explorer for this Smart contract address"));
		getReceiptBtn.setOnAction(_ -> getTransactionReceipt());
		getReceiptHelpBtn.setOnAction(_ -> windowLauncher.launchHelpSpecialWindow("Will retrieve ethereum transaction receipt for inputted transaction hash"));
		transactionHashField.textProperty().addListener((_, _, newValue) -> getReceiptBtn.setDisable(newValue.isEmpty()));
	}

	private void updateButtonsByStatus(ContractStatus status) {
		gasResultLabel.setText("");
		receiptTextArea.clear();

		switch (status) {
			case SOLIDITY_GENERATED -> {
				estimateGasBtn.setDisable(false);
				deployContractBtn.setDisable(false);
			}
			case DEPLOYED -> checkConfirmedBtn.setDisable(false);
			case CONFIRMED, TERMINATED -> {
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
		estimateGasBtn.setDisable(true);
		getReceiptBtn.setDisable(true);
		balanceLabel.setText("Balance: —");
	}

	private void estimateGas() {
		promptForAbiParams(contract.abi(), "constructor", true).thenAccept(abiResult -> {
			try {
				ResponseEntity<GasEstimateResponseDTO> response = httpClientUtil.post(baseUrl + "/estimate-gas", null, new DeploymentRequestDTO(contract.id(), abiResult.getLeft(), abiResult.getRight()), GasEstimateResponseDTO.class);

				if(response.getBody() == null) throw new NoHttpResponseException("Gas estimation failed with no response");
				else if (response.getStatusCode().is2xxSuccessful())
					Platform.runLater(() -> {
						gasResultLabel.setText("Gas Limit: " + String.format("%,d", response.getBody().gasLimit()) +
								"\nGas Price: " + Convert.fromWei(new BigDecimal(response.getBody().gasPriceWei()), Convert.Unit.GWEI)
								.setScale(6, RoundingMode.HALF_UP).toPlainString() + " Gwei" +
								"\nEstimated Cost: " + Convert.fromWei(new BigDecimal(response.getBody().gasPriceWei().multiply(response.getBody().gasLimit())), Convert.Unit.ETHER)
								.setScale(6, RoundingMode.HALF_UP).toPlainString() + " ETH");
						windowLauncher.launchSuccessSpecialWindow("Gas estimation completed successfully for contract: " + contract.id());
					});
				else throw new HttpResponseException(response.getStatusCode().value(), response.getBody().message());
			} catch (Exception e) {
				log.error("Could not estimate gas price and limit for deploying contract", e);
				windowLauncher.launchErrorSpecialWindow("Error occurred while estimating gas required to deploy a contract:\n" + e.getLocalizedMessage());
			}
        }).exceptionally(e -> {
			log.error("Could not estimate gas price and limit for deploying contract", e);
			windowLauncher.launchErrorSpecialWindow("Error occurred while estimating gas required to deploy a contract:\n" + e.getLocalizedMessage());
			return null;
		});
	}

	private void deployContract() {
		promptForAbiParams(contract.abi(), "constructor", true).thenAccept(abiResult -> {
			try {
				ResponseEntity<String> response = httpClientUtil.post(baseUrl + "/deploy-contract", null, new DeploymentRequestDTO(contract.id(), abiResult.getLeft(), abiResult.getRight()), String.class);

				if (response.getBody() == null) throw new NoHttpResponseException("Contract deployment failed with no response");
				else if (response.getStatusCode().is2xxSuccessful())
					Platform.runLater(() -> {
						mainRefreshBtn.fire();
						windowController.getCloseBtn().fire();
						windowLauncher.launchSuccessSpecialWindow(response.getBody());
					});
				else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
			} catch (Exception e) {
				log.error("Could not deploy smart contract", e);
				windowLauncher.launchErrorSpecialWindow("Error occurred while deploying contract:\n" + e.getLocalizedMessage());
			}
		}).exceptionally(e -> {
			log.error("Could not deploy smart contract", e);
			windowLauncher.launchErrorSpecialWindow("Error occurred while deploying contract:\n" + e.getLocalizedMessage());
			return null;
		});
	}

	private void checkConfirmation() {
		try {
			ResponseEntity<String> response = httpClientUtil.get(baseUrl + "/" + contract.deployedAddress() + "/exists", null, String.class);

			if(response.getBody() == null) throw new NoHttpResponseException("Deployed contract confirmation check failed with no response");
			else if (response.getStatusCode().is2xxSuccessful())
				Platform.runLater(() -> {
					if (response.getBody().equals("true")) {
						mainRefreshBtn.fire();
						windowController.getCloseBtn().fire();
						windowLauncher.launchSuccessSpecialWindow("Smart contract deployed at address \"" + contract.deployedAddress() + "\" has been confirmed on Blockchain");
					} else windowLauncher.launchSuccessSpecialWindow("Smart contract deployed at address \"" + contract.deployedAddress() + "\" have not yet been confirmed on Blockchain");
				});
			else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
		} catch (Exception e) {
			log.error("Could not check if smart contract has been deployed", e);
			windowLauncher.launchErrorSpecialWindow("Error occurred while checking deployed contract confirmation:\n" + e.getLocalizedMessage());
		}
	}

	private void viewContractOnBlockchain() {
		String address = contract.deployedAddress();
		if (address != null && !address.isBlank()) windowLauncher.launchWebViewWindow("Smart Contract view on Blockchain - " + contract.id(), 1024, 1024, String.format("%s/address/%s", etherscanUrl, address));
		else windowLauncher.launchWarnSpecialWindow("Cannot view contract on Blockchain cause no ethereum address was provided");
	}

	private void getTransactionReceipt() {
		try {
			ResponseEntity<String> response = httpClientUtil.get(baseUrl + "/transaction/" + transactionHashField.getText() +"/receipt", null, String.class);

			if(response.getBody() == null) throw new NoHttpResponseException("Action to get transaction receipt failed with no response");
			else if (response.getStatusCode().is2xxSuccessful())Platform.runLater(() -> receiptTextArea.setText(response.getBody()));
			else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
		} catch (Exception e) {
			log.error("Could not get requested blockchain transaction receipt", e);
			windowLauncher.launchErrorSpecialWindow("Error occurred while getting transaction receipt:\n" + e.getLocalizedMessage());
		}
	}

	private CompletableFuture<Pair<String, List<Object>>> promptForAbiParams(String abiJson, String targetNameOrType, boolean isConstructor) {
		CompletableFuture<Pair<String, List<Object>>> resultFuture = new CompletableFuture<>();

		try {
			ConstructorInputController controller = applicationContext.getBean(ConstructorInputController.class);
			controller.setRetrievedParamsFuture(resultFuture);

			ResponseEntity<List<WalletInfo>> response = httpClientUtil.get(baseUrl + "/getAvailableWallets", null, new TypeToken<List<WalletInfo>>() {}.getType());
			if (response.getBody() == null)
				throw new HttpResponseException(HttpStatus.NO_CONTENT.value(), "Response body is null");
			else if (!response.getStatusCode().is2xxSuccessful())
				throw new HttpResponseException(response.getStatusCode().value(), response.getBody().getFirst().label());
			else if (response.getBody().isEmpty())
				windowLauncher.launchWarnSpecialWindow("No ethereum wallets found.");

			controller.setWalletInfos(response.getBody());

			// Open window to get user input for ABI parameters
			controller = windowLauncher.launchWindow(isConstructor ? "Constructor Parameters" : "Function Parameters", 500, 500, "/layout/constructor_input.fxml",
					Objects.requireNonNull(getClass().getResource("/static/style/constructor_input.css")).toExternalForm(), controller);

			controller.loadParamInputs(abiJson, targetNameOrType, isConstructor);
		} catch (Exception e) {
			log.error("Error while prompting for ABI params", e);
			resultFuture.completeExceptionally(e);
		}

		return resultFuture;
	}

	private void refreshBalance() {
		try {
			ResponseEntity<String> response = httpClientUtil.get(baseUrl + "/" + contract.deployedAddress() + "/balance", null, String.class);

			if(response.getBody() == null) throw new NoHttpResponseException("Smart contract current balance retrieval failed with no response");
			else if (response.getStatusCode().is2xxSuccessful()) Platform.runLater(() -> balanceLabel.setText("Balance 🪙: " + response.getBody() + " ETH"));
			else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
		} catch (Exception e) {
			log.error("Could not retrieve current smart contract balance", e);
			windowLauncher.launchErrorSpecialWindow("Error occurred while smart contract current balance retrieval:\n" + e.getLocalizedMessage());
		}
	}

	private void invokeFunction(String smartContractFunction, String callerWallet, List<Object> params, BigInteger valueWei) {
		try {
			ResponseEntity<String> response = httpClientUtil.post(baseUrl + "/" + contract.deployedAddress() + "/invoke", null, new ContractFunctionRequestDTO(smartContractFunction, params, valueWei.toString(), callerWallet), String.class);

			if(response.getBody() == null) throw new NoHttpResponseException("Deployed contract confirmation check failed with no response");
			else if (response.getStatusCode().is2xxSuccessful())
				Platform.runLater(() -> {
					transactionHashField.setText(response.getBody());
					refreshBalance();
					mainRefreshBtn.fire();
					windowLauncher.launchSuccessSpecialWindow("Action successfully invoked upon smart contract. Transaction hash field has been populated corresponding transaction hash id");
				});
			else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
		} catch (Exception e) {
			log.error(e.getLocalizedMessage());
			throw new InvalidFunctionCallException("Error occurred while invoking action upon smart contract:\n" + e.getLocalizedMessage());
		}
	}

	private void startAutoRefreshBalance() {
		if (autoRefreshTimeline != null) autoRefreshTimeline.stop();

		autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(30), _ -> refreshBalance()));
		autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
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

				String smartContractFunctionName = obj.get("name").getAsString();
				String stateMutability = obj.get("stateMutability").getAsString();

				// Only non-constant functions get buttons
				if (!"view".equals(stateMutability) && !"pure".equals(stateMutability) && contract.status() != ContractStatus.TERMINATED) additionalActionButtonPane.getChildren().add(generateSmartContractFunctionStackPane(smartContractFunctionName, stateMutability));
				else
					if ("function".equals(obj.get("type").getAsString()) && obj.has("name") && obj.has("outputs")) {
						JsonArray outputs = obj.get("outputs").getAsJsonArray();
						if (outputs.size() == 1 && "address".equals(outputs.get(0).getAsJsonObject().get("type").getAsString()) && obj.get("inputs").getAsJsonArray().isEmpty()) functionGetters.add(obj.get("name").getAsString());
					}
			}

			if (!functionGetters.isEmpty()) {
				ResponseEntity<ContractPartiesAddressDataResponseDTO> response = httpClientUtil.post(baseUrl + "/resolveContractPartiesAddressData", null, new ContractPartiesAddressDataRequestDTO(contract.deployedAddress(), functionGetters), ContractPartiesAddressDataResponseDTO.class);

				if (response.getBody() != null && response.getStatusCode().is2xxSuccessful()) {
					Map<String, String> resultMap = response.getBody().resolvedAddresses();

					VBox getterBox = new VBox(5);
					getterBox.setPadding(new Insets(10));
					getterBox.getChildren().add(new Label("Involved parties (• identification → address):"));

					for (var entry : resultMap.entrySet()) {
						getterBox.getChildren().add(new Label("• " + entry.getKey() + " → " + entry.getValue()));
					}

					Platform.runLater(() -> additionalActionButtonPane.getChildren().add(getterBox));
				} else windowLauncher.launchWarnSpecialWindow("Confirm contract UI generation cannot get involved parties data:\n" + response.getBody().resolvedAddresses().values().iterator().next());
			}

		} catch (Exception e) {
			log.error("Confirm contract UI generation failure", e);
			windowLauncher.launchErrorSpecialWindow("Confirm contract UI generation failure\n" + e.getLocalizedMessage());
		}
	}

	private @NotNull StackPane generateSmartContractFunctionStackPane(String smartContractFunctionName, String stateMutability) {
		final Button smartContractFunctionButton = new Button(smartContractFunctionName);
		final Button smartContractFunctionHelpButton = new Button("?");
		final StackPane smartContractFunctionButtonStackPane = new StackPane(smartContractFunctionButton, smartContractFunctionHelpButton);

		smartContractFunctionButton.getStyleClass().add("btn-action");
		smartContractFunctionHelpButton.getStyleClass().add("btn-help");
		smartContractFunctionHelpButton.setPrefSize(20, 20);
		smartContractFunctionHelpButton.setMinSize(20, 20);
		smartContractFunctionHelpButton.setMaxSize(20, 20);
		smartContractFunctionHelpButton.setFocusTraversable(false);
		smartContractFunctionHelpButton.setTranslateX(10);
		smartContractFunctionHelpButton.setTranslateY(-10);
		StackPane.setAlignment(smartContractFunctionHelpButton, Pos.TOP_RIGHT);

		smartContractFunctionButton.setOnAction(_ -> promptForAbiParams(contract.abi(), smartContractFunctionName, false).thenAccept(abiResult -> {
            try {
                // Prompt user for all params including caller wallet as first param
                if (abiResult.getLeft().isEmpty() && abiResult.getRight().isEmpty()) {
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
                    dialog.setContentText("Amount in ETH:");

                    AtomicReference<BigInteger> weiAmount = new AtomicReference<>(BigInteger.ZERO);

                    dialog.showAndWait().ifPresentOrElse(eth -> {
                        // Use safer regex to validate ETH amount format
                        if (!eth.matches("^(?:\\d+(?:\\.\\d+)?|\\.\\d+)$")) throw new IllegalArgumentException("Invoking action failure: Invalid ETH amount");
                        weiAmount.set(Convert.toWei(eth, Convert.Unit.ETHER).toBigIntegerExact());
                    }, () -> { throw new IllegalArgumentException("Invoking action failure: ETH payment required for payable function"); });


                    valueWei = weiAmount.get();
                }

                invokeFunction(smartContractFunctionName, abiResult.getLeft(), abiResult.getRight(), valueWei);
            } catch (IllegalArgumentException illegalArgumentException){
                log.warn("Invoking action failure", illegalArgumentException);
                windowLauncher.launchWarnSpecialWindow("Invoking action failure:\n" + illegalArgumentException.getLocalizedMessage());
            } catch (Exception e) {
                log.error("Cannot invoke action upon smart contract", e);
                windowLauncher.launchErrorSpecialWindow(e.getLocalizedMessage());
            }
        }).exceptionally(e -> {
			log.error("Cannot invoke action upon smart contract", e);
			windowLauncher.launchErrorSpecialWindow(e.getLocalizedMessage());
			return null;
		}));
		smartContractFunctionHelpButton.setOnAction(_ -> windowLauncher.launchHelpSpecialWindow(String.format("Will invoke \"%s\" action upon Smart contract.%nIf action requires parameters (such as action invoker Ethereum wallet address, amount of Ethereum to transfer, etc.) those will be asked for in popup window.%nFor detailed information what this function does check Solidity code in application or Blockchain explorer", smartContractFunctionName)));
		return smartContractFunctionButtonStackPane;
	}
}