package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import dev.markodojkic.legalcontractdigitizer.LegalContractDigitizerApplication;
import dev.markodojkic.legalcontractdigitizer.enums_records.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.AuthSession;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static dev.markodojkic.legalcontractdigitizer.enums_records.ContractStatus.*;

@Component
@Slf4j
public class MainController extends WindowAwareController {
    @FXML private Label nameLabel;
    @FXML private Label emailLabel;
    @FXML private Label userIdLabel;
    @FXML private Button uploadBtn;
    @FXML private Button refreshBtn;
    @FXML private Button logoutBtn;
    @FXML private Button walletsManagerBtn;

    @FXML private TableView<DigitalizedContract> contractsTable;
    @FXML private TableColumn<DigitalizedContract, String> idCol;
    @FXML private TableColumn<DigitalizedContract, Void> actionCol;
    @FXML private TableColumn<DigitalizedContract, String> statusCol;

    private final Preferences prefs = Preferences.userNodeForPackage(LegalContractDigitizerApplication.class);
    private final HttpClientUtil httpClientUtil;
    private final String baseUrl;

    @Autowired
    public MainController(@Value("${server.port}") Integer serverPort,
                          WindowLauncher windowLauncher,
                          ApplicationContext applicationContext,
                          HttpClientUtil httpClientUtil){
        super(windowLauncher, applicationContext);
        this.baseUrl = String.format("http://localhost:%s/api/v1/contracts", serverPort);
        this.httpClientUtil = httpClientUtil;
    }

    @FXML
    public void initialize() {
        nameLabel.setText("Logged in as: " + prefs.get("name", "N/A"));
        userIdLabel.setText("Google ID: " + prefs.get("userId", "N/A"));
        emailLabel.setText("Email:" + prefs.get("email", "N/A"));

        setupTable();

        uploadBtn.setOnAction(_ -> windowLauncher.launchFilePickerWindow("Upload New Contract", 400, 200, file -> {
            try {
                ResponseEntity<String> response = httpClientUtil.postWithFile(
                        baseUrl + "/upload",
                        null,
                        "file",
                        file,
                        String.class
                );

                if(response.getBody() == null) throw new NoHttpResponseException("New contract upload failed with no response");
                else if (response.getStatusCode().is2xxSuccessful()) refreshContracts();
                else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
            } catch (Exception e) {
                log.error(e.getLocalizedMessage());
                windowLauncher.launchErrorSpecialWindow("Error occurred while uploading new contract:\n" + e.getLocalizedMessage());
            }
        }));
        walletsManagerBtn.setOnAction(e -> openWalletsManager());
        refreshBtn.setOnAction(e -> refreshContracts());
        logoutBtn.setOnAction(_ -> {
            try {
                Preferences.userNodeForPackage(LegalContractDigitizerApplication.class).clear();
            } catch (BackingStoreException e) {
                log.error(e.getLocalizedMessage());
                windowLauncher.launchWarnSpecialWindow("Error occurred while clearing user data:\n" + e.getLocalizedMessage());
            }

            AuthSession.logout();

            Platform.runLater(() -> {
                windowLauncher.launchWindow("Login window", 500, 500, "/layout/login.fxml", Objects.requireNonNull(getClass().getResource("/static/style/login.css")).toExternalForm(), applicationContext.getBean(LoginController.class));
                windowController.getCloseButton().fire();
                windowLauncher.launchSuccessSpecialWindow("User logged out");
            });
        });
        refreshContracts(); // auto-load
    }

    private void openWalletsManager() {
        windowLauncher.launchWindow(
                "Ethereum wallets manager",
                850,
                500,
                "/layout/wallet_manager.fxml",
                null,
                applicationContext.getBean(WalletManagerController.class)
        );
    }

    private void setupActionColumn() {
        actionCol.setCellFactory(col -> new TableCell<DigitalizedContract, Void>() {
            private final Button nextStepBtn = new Button();
            private final Button viewClausesBtn = new Button("View Clauses");
            private final Button viewSolidityBtn = new Button("View Solidity");
            private final Button deleteBtn = new Button("Delete");

            private final HBox container = new HBox(8);

            {
                nextStepBtn.getStyleClass().add("btn-action");
                viewClausesBtn.getStyleClass().add("btn-info");
                viewSolidityBtn.getStyleClass().add("btn-info");
                deleteBtn.getStyleClass().add("btn-danger");
                container.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                container.getChildren().clear();

                nextStepBtn.setOnAction(e -> performNextStep(getTableView().getItems().get(getIndex())));

                viewClausesBtn.setOnAction(e -> fetchAndShowClauses(getTableView().getItems().get(getIndex())));

                viewSolidityBtn.setOnAction(e -> fetchAndShowSolidity(getTableView().getItems().get(getIndex())));

                deleteBtn.setOnAction(_ -> {
                    try {
                        ResponseEntity<String> response = httpClientUtil.delete(baseUrl + "/" + getTableView().getItems().get(getIndex()).id(), null, Void.class);
                        if (response.getStatusCode().is2xxSuccessful()) {
                            contractsTable.getItems().remove(getTableView().getItems().get(getIndex()));
                            refreshContracts();
                        } else if(response.getStatusCode() == HttpStatus.CONFLICT)
                            windowLauncher.launchWarnSpecialWindow("Cannot delete: contract is already confirmed.");
                        else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
                    } catch (Exception e) {
                        log.error(e.getLocalizedMessage());
                        windowLauncher.launchErrorSpecialWindow("Deletion failed due to exception:\n" + e.getLocalizedMessage());
                    }
                });

                switch (getTableView().getItems().get(getIndex()).status()) {
                    case UPLOADED -> {
                        nextStepBtn.setText("Extract Clauses");
                        container.getChildren().add(nextStepBtn);
                    }
                    case CLAUSES_EXTRACTED -> {
                        nextStepBtn.setText("Prepare Solidity");
                        container.getChildren().add(nextStepBtn);
                    }
                    case SOLIDITY_PREPARED -> {
                        nextStepBtn.setText("Generate Solidity");
                        container.getChildren().add(nextStepBtn);
                    }
                    case SOLIDITY_GENERATED,
                         DEPLOYED,
                         CONFIRMED,
                         TERMINATED -> {
                        nextStepBtn.setText("Ethereum Actions");
                        nextStepBtn.getStyleClass().add("btn-action");
                        nextStepBtn.setOnAction(e -> {
                            EthereumActionsController controller = applicationContext.getBean(EthereumActionsController.class);
                            controller.setContract(getTableView().getItems().get(getIndex()));
                            windowLauncher.launchWindow(
                                    "Ethereum Actions",
                                    500,
                                    800,
                                    "/layout/ethereum_actions.fxml",
                                    Objects.requireNonNull(getClass().getResource("/static/style/ethereum_actions.css")).toExternalForm(),
                                    controller
                            );
                        });
                        container.getChildren().add(nextStepBtn);
                    }
                }

                if (getTableView().getItems().get(getIndex()).status().compareTo(CLAUSES_EXTRACTED) >= 0) container.getChildren().add(viewClausesBtn);
                if (getTableView().getItems().get(getIndex()).status().compareTo(SOLIDITY_PREPARED) >= 0) container.getChildren().add(viewSolidityBtn);
                if (getTableView().getItems().get(getIndex()).status().compareTo(CONFIRMED) < 0) container.getChildren().add(deleteBtn);

                setGraphic(container);
            }
        });
    }

    private void setupTable() {
        contractsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        contractsTable.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            // Calculate total width of columns except last
            double otherColumnsWidth = 0;
            for (int i = 0; i < contractsTable.getColumns().size() - 1; i++) {
                otherColumnsWidth += contractsTable.getColumns().get(i).getWidth();
            }
            // Set last column width to fill remaining space
            double lastColWidth = newWidth.doubleValue() - otherColumnsWidth - 2; // -2 for padding/borders
            if (lastColWidth > 0) {
                contractsTable.getColumns().getLast().setPrefWidth(lastColWidth);
            }
        });
        idCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().id()));
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status().toString()));

        setupActionColumn();

        contractsTable.setRowFactory(tv -> new TableRow<DigitalizedContract>() {
            @Override
            protected void updateItem(DigitalizedContract item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(Arrays.stream(values()).map(Enum::name).toList());
                if(!empty && item != null && item.status() != null) getStyleClass().add(item.status().toString());
            }
        });
    }

    private void refreshContracts() {
        try {
            ResponseEntity<DigitalizedContract[]> response = httpClientUtil.get(baseUrl + "/list?userId=" + prefs.get("userId", ""), null, DigitalizedContract[].class);

            if(response.getBody() == null) throw new NoHttpResponseException("Listing user contracts failed with no response");
            else if (response.getStatusCode().is2xxSuccessful()) Platform.runLater(() -> contractsTable.getItems().setAll(response.getBody()));
            else throw new HttpResponseException(response.getStatusCode().value(), "Failed to load contracts");
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
            windowLauncher.launchErrorSpecialWindow("Error occurred while reloading contracts:\n" + e.getLocalizedMessage());
        }
    }

    private void performNextStep(DigitalizedContract contract) {
        String url;

        switch (contract.status()) {
            case UPLOADED:
                url = baseUrl + "/extract-clauses?contractId=" + contract.id();
                break;

            case CLAUSES_EXTRACTED, SOLIDITY_PREPARED:
                url = baseUrl + "/generate-solidity?contractId=" + contract.id();
                break;

            default:
                return; // No action for CONFIRMED or unknown
        }

        try {
            // Empty body for PATCH can be null or empty map depending on your implementation
            ResponseEntity<String> response = httpClientUtil.patch(url, null, null, String.class);

            if(response.getBody() == null) throw new NoHttpResponseException((contract.status() == UPLOADED ? "Clauses extraction" : "Action related to solidity contract") + " failed with no response");
            else if (response.getStatusCode().is2xxSuccessful()) {
                refreshContracts();
                if(response.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(HttpStatus.PARTIAL_CONTENT.value()))) windowLauncher.launchWarnSpecialWindow(response.getBody());
                else windowLauncher.launchSuccessSpecialWindow(response.getBody());
            } else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
            windowLauncher.launchErrorSpecialWindow("Error occurred while performing action upon contract:\n" + e.getLocalizedMessage());
        }
    }

    private void fetchAndShowClauses(DigitalizedContract contract) {
        List<String> clauses = contract.extractedClauses();
        if (clauses == null || clauses.isEmpty()) {
            log.warn("No clauses available for contract {}", contract.id());
            windowLauncher.launchWarnSpecialWindow("No clauses available for contract: " + contract.id());
        } else {
            ClausesViewController controller = applicationContext.getBean(ClausesViewController.class);
            controller.addClauses(clauses);

            windowLauncher.launchWindow(
                    "Extracted legal clauses",
                    800,
                    800,
                    "/layout/clauses_view.fxml",
                    Objects.requireNonNull(getClass().getResource("/static/style/clauses_view.css")).toExternalForm(),
                    controller
            );
        }
    }


    private void fetchAndShowSolidity(DigitalizedContract contract) {
        String soliditySource = contract.soliditySource();
        if (soliditySource == null) {
            log.warn("Solidity source not available for contract {}", contract.id());
            windowLauncher.launchWarnSpecialWindow("No solidity source available for contract: " + contract.id());
        } else {
            WindowPreviewController controller = applicationContext.getBean(WindowPreviewController.class);
            controller.setText(soliditySource);

            windowLauncher.launchWindow(
                    "Generated Solidity contract",
                    800,
                    800,
                    "/layout/window_preview.fxml",
                    Objects.requireNonNull(getClass().getResource("/static/style/window_preview.css")).toExternalForm(),
                    controller
            );
        }
    }
}