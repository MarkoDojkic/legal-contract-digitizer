package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.gson.reflect.TypeToken;
import dev.markodojkic.legalcontractdigitizer.model.WalletInfo;
import dev.markodojkic.legalcontractdigitizer.exception.WalletCreationException;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class WalletManagerController extends WindowAwareController {
	@FXML private TextField walletLabelField;
	@FXML private Button registerWalletBtn, registerWalletHelpBtn;
	@FXML private TableView<WalletInfo> walletTable;
	@FXML private TableColumn<WalletInfo, String> labelColumn, addressColumn;
	@FXML private TableColumn<WalletInfo, BigDecimal> balanceColumn;
	@FXML private TableColumn<WalletInfo, Void> actionColumn;

	private final HttpClientUtil httpClientUtil;
	private final String baseUrl, etherscanUrl;

	@Autowired
	public WalletManagerController(@Value("${server.port}") Integer serverPort, @Value("${ethereum.etherscan.url}") String etherscanUrl, WindowLauncher windowLauncher, ApplicationContext applicationContext, HttpClientUtil httpClientUtil){
		super(windowLauncher, applicationContext);
		this.baseUrl = String.format("http://localhost:%s/api/v1/ethereum", serverPort);
		this.etherscanUrl = etherscanUrl;
		this.httpClientUtil = httpClientUtil;
	}

	@FXML
	private void initialize() {
		labelColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().label()));
		addressColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().address()));
		balanceColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().balance()));
		registerWalletBtn.setOnAction(_ -> registerWallet());
		registerWalletHelpBtn.setOnAction(_ -> windowLauncher.launchHelpSpecialWindow("Will initiate creation of new Ethereum wallet on previously configured blockchain (or Sepolia testnet by default).\n Upon completion private key will be stored locally, label is used for local identification only. \n This action is irreversible!"));

		labelColumn.setCellFactory(createCenteredCellFactoryAndFormat(null));
		addressColumn.setCellFactory(createCenteredCellFactoryAndFormat(null));
		balanceColumn.setCellFactory(createCenteredCellFactoryAndFormat("%s ETH"));

		actionColumn.setCellFactory(_ -> new TableCell<>() {
			private final Button viewBtn = new Button("👁"), viewHelpBtn = new Button("?");
			private final StackPane stackPane = new StackPane(viewBtn, viewHelpBtn);
			{
				viewHelpBtn.getStyleClass().add("btn-help");
				viewHelpBtn.setPrefSize(20, 20);
				viewHelpBtn.setFocusTraversable(false);
				viewHelpBtn.setTranslateX(5);
				viewHelpBtn.setTranslateY(-5);

				StackPane.setAlignment(viewHelpBtn, Pos.TOP_RIGHT);

				viewBtn.setOnAction(_ -> {
					WalletInfo wallet = getTableView().getItems().get(getIndex());
					windowLauncher.launchWebViewWindow(
							"View on Blockchain - " + wallet.label(),
							1024,
							1024,
							String.format("%s/address/%s", etherscanUrl, wallet.address())
					);
				});

				viewHelpBtn.setOnAction(_ -> windowLauncher.launchHelpSpecialWindow("Will open previously configured blockchain (or Sepolia testnet by default) explorer for this row Ethereum address"));
			}

			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				setGraphic(empty ? null : stackPane);
			}
		});

		loadWallets();
	}

	private void registerWallet() {
		String label = walletLabelField.getText().trim();
		if (label.isEmpty()) {
			windowLauncher.launchWarnSpecialWindow("New wallet must have label");
			return;
		}

		try {
			ResponseEntity<WalletInfo> response = httpClientUtil.post(baseUrl + "/register?label=" + label, null, null, WalletInfo.class);

			if (response.getBody() != null && response.getStatusCode().is2xxSuccessful()) {
				walletTable.getItems().add(response.getBody());
				walletLabelField.clear();
			} else throw new WalletCreationException(response.getBody().label());
		} catch (Exception e) {
			log.error("Error occurred while creating new wallet", e);
			windowLauncher.launchErrorSpecialWindow("Error occurred while creating new wallet:\n" + e.getLocalizedMessage());
		}
	}

	private void loadWallets() {
		try {
			ResponseEntity<List<WalletInfo>> response = httpClientUtil.get(
					baseUrl + "/getAvailableWallets",
					null,
					new TypeToken<List<WalletInfo>>() {
					}.getType()
			);

			if(response.getBody() == null) windowLauncher.launchWarnSpecialWindow("No ethereum wallets found.");
			else if(!response.getStatusCode().is2xxSuccessful()) throw new HttpResponseException(response.getStatusCode().value(), Objects.requireNonNull(response.getBody()).getFirst().label());
			else Platform.runLater(() -> walletTable.getItems().setAll(response.getBody()));
		} catch (Exception e) {
			log.error("Error occurred while loading wallets", e);
			windowLauncher.launchErrorSpecialWindow("Error occurred while loading wallets:\n" + e.getLocalizedMessage());
		}
	}

	private static <T, V> Callback<TableColumn<T, V>, TableCell<T, V>> createCenteredCellFactoryAndFormat(@Nullable String format) {
		return _ -> new TableCell<>() {
			@Override
			protected void updateItem(V item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
				} else {
					setText(String.format(Optional.ofNullable(format).orElse("%s"), item));
					setAlignment(Pos.CENTER);
				}
			}
		};
	}

}