package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.gson.reflect.TypeToken;
import dev.markodojkic.legalcontractdigitizer.model.WalletInfo;
import dev.markodojkic.legalcontractdigitizer.exception.WalletCreationException;
import dev.markodojkic.legalcontractdigitizer.exception.WalletNotFoundException;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class WalletManagerController extends WindowAwareController {
	@FXML private TextField walletLabelField;
	@FXML private Button registerButton;
	@FXML private TableView<WalletInfo> walletTable;
	@FXML private TableColumn<WalletInfo, String> labelColumn;
	@FXML private TableColumn<WalletInfo, String> addressColumn;
	@FXML private TableColumn<WalletInfo, BigDecimal> balanceColumn;
	@FXML private TableColumn<WalletInfo, Void> actionColumn;

	private final HttpClientUtil httpClientUtil;
	private final String baseUrl;
	private final String etherscanUrl;

	@Autowired
	public WalletManagerController(@Value("${server.port}") Integer serverPort,
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
		labelColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().label()));
		addressColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().address()));
		balanceColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().balance()));
		registerButton.setOnAction(e -> registerWallet());

		labelColumn.setCellFactory(_ -> new TableCell<>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
				} else {
					setText(item);
					setAlignment(Pos.CENTER);
				}
			}
		});

		addressColumn.setCellFactory(_ -> new TableCell<>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
				} else {
					setText(item);
					setAlignment(Pos.CENTER);
				}
			}
		});

		balanceColumn.setCellFactory(_ -> new TableCell<WalletInfo, BigDecimal>() {
			@Override
			protected void updateItem(BigDecimal item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
				} else {
					setText(item + " Sepolia ETH");
					setAlignment(Pos.CENTER);
				}
			}
		});

		actionColumn.setCellFactory(col -> new TableCell<>() {
			private final Button viewBtn = new Button("\uD83D\uDC41");

			{
				viewBtn.setOnAction(e -> {
					WalletInfo wallet = getTableView().getItems().get(getIndex());
					windowLauncher.launchWebViewWindow(
							"View on Blockchain - " + wallet.label(),
							1024,
							1024,
							String.format("%s/address/%s", etherscanUrl, wallet.address())
					);
				});
			}

			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				setGraphic(empty ? null : viewBtn);
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
			ResponseEntity<WalletInfo> response = httpClientUtil.post(
					baseUrl + "/register?label=" + label,
					null,
					null,
					WalletInfo.class
			);

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

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) Platform.runLater(() -> walletTable.getItems().setAll(response.getBody()));
			else throw new WalletNotFoundException("No ethereum wallets found.");
		} catch (Exception e) {
			log.error("Error occurred while loading wallets", e);
			windowLauncher.launchErrorSpecialWindow("Error occurred while loading wallets:\n" + e.getLocalizedMessage());
		}
	}
}
