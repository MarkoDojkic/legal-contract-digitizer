package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import dev.markodojkic.legalcontractdigitizer.dto.ContractPartiesBalanceRequest;
import dev.markodojkic.legalcontractdigitizer.dto.PartyBalanceDto;
import dev.markodojkic.legalcontractdigitizer.enums_records.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.List;

@Component
@Slf4j
public class ContractPartiesBalancesController implements WindowAwareController {
	@Getter
	@Setter
	private JavaFXWindowController windowController;

	@FXML private TableView<PartyBalanceDto> partiesTable;
	@FXML private TableColumn<PartyBalanceDto, String> roleColumn;
	@FXML private TableColumn<PartyBalanceDto, String> addressColumn;
	@FXML private TableColumn<PartyBalanceDto, String> balanceColumn;
	@FXML private TableColumn<PartyBalanceDto, Void> actionColumn;
	@FXML private Button refreshBtn;

	@Setter private DigitalizedContract contract;
	private final WindowLauncher windowLauncher;
	private final String baseUrl;
	private final String etherscanUrl;

	public ContractPartiesBalancesController(@Value("${server.port}") Integer serverPort,
	                                 @Value("${ethereum.etherscan.url}") String etherscanUrl,
	                                 @Autowired WindowLauncher windowLauncher){
		this.baseUrl = String.format("http://localhost:%s/api/v1/ethereum", serverPort);
		this.etherscanUrl = etherscanUrl;
		this.windowLauncher = windowLauncher;
	}

	@FXML
	public void initialize() {
		roleColumn.setCellValueFactory(new PropertyValueFactory<>("roleName"));
		addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
		balanceColumn.setCellValueFactory(cell -> {
			var eth = cell.getValue().getBalanceEth().setScale(6, RoundingMode.HALF_UP);
			return new ReadOnlyStringWrapper(eth.toPlainString());
		});
		refreshBtn.setOnAction(e -> refreshBalances());

		addActionButtons();
		refreshBalances();
	}

	private void addActionButtons() {
		actionColumn.setCellFactory(col -> new TableCell<>() {
			private final Button viewBtn = new Button("🔗");

			{
				viewBtn.setOnAction(e -> {
					PartyBalanceDto party = getTableView().getItems().get(getIndex());
					windowLauncher.launchWebViewWindow(
							new Stage(),
							"Smart Contract view on Blockchain - " + contract.id(),
							1024,
							1024,
							String.format("%s/address/%s", etherscanUrl, party.getAddress())
					);
				});
			}

			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				setGraphic(empty ? null : viewBtn);
			}
		});
	}

	private void refreshBalances() {
		try {
			var request = ContractPartiesBalanceRequest.builder()
					.contractAddress(contract.deployedAddress())
					.abi(contract.abi())
					.build();

			ResponseEntity<PartyBalanceDto[]> response = HttpClientUtil.post(
					baseUrl + "/parties-balances",
					null,
					request,
					PartyBalanceDto[].class
			);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				List<PartyBalanceDto> balances = List.of(response.getBody());
				Platform.runLater(() -> partiesTable.getItems().setAll(balances));
			} else {
				Platform.runLater(() -> showError("Failed to fetch balances"));
			}
		} catch (Exception e) {
			log.error("Error fetching balances", e);
			Platform.runLater(() -> showError("Error fetching balances: " + e.getMessage()));
		}
	}

	private void showError(String msg) {
		Platform.runLater(() -> {
			Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
			alert.showAndWait();
		});
	}
}
