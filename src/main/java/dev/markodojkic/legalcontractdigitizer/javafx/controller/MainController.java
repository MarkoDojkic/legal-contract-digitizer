package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import dev.markodojkic.legalcontractdigitizer.LegalContractDigitizerApplication;
import dev.markodojkic.legalcontractdigitizer.dto.UploadResponseDTO;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static dev.markodojkic.legalcontractdigitizer.enumsAndRecords.ContractStatus.*;

@Component
@Slf4j
public class MainController implements WindowAwareController {
    @Setter
    @Getter
    private JavaFXWindowController windowController;

    @FXML private Label nameLabel;
    @FXML private Label emailLabel;
    @FXML private Label userIdLabel;
    @FXML private Button uploadBtn;
    @FXML private Button refreshBtn;
    @FXML private Button logoutBtn;

    @FXML private TableView<DigitalizedContract> contractsTable;
    @FXML private TableColumn<DigitalizedContract, String> idCol;
    @FXML private TableColumn<DigitalizedContract, Void> actionCol;
    @FXML private TableColumn<DigitalizedContract, String> statusCol;

    private final WindowLauncher windowLauncher;
    private final ApplicationContext applicationContext;
    private String baseUrl;

    @Setter
    private Map<String, String> userData;

    public MainController(@Value("${server.port}") Integer serverPort,
                          @Autowired WindowLauncher windowLauncher,
                          @Autowired ApplicationContext applicationContext){
        this.baseUrl = String.format("http://localhost:%s/api/v1/contracts", serverPort);
        this.windowLauncher = windowLauncher;
        this.applicationContext = applicationContext;
    }

    @FXML
    public void initialize() {
        if (userData != null) {
            nameLabel.setText(userData.getOrDefault("name", "N/A"));
            emailLabel.setText(userData.getOrDefault("email", "N/A"));
            userIdLabel.setText(userData.getOrDefault("userId", "N/A"));
        }

        setupTable();

        uploadBtn.setOnAction(e -> {
            Stage stage = new Stage();
            windowLauncher.launchFilePickerWindow(stage, "Upload New Contract", 400, 200, file -> {
                try {
                    ResponseEntity<UploadResponseDTO> response = HttpClientUtil.postWithFile(
                            baseUrl + "/upload",
                            null,
                            "file",
                            file,
                            UploadResponseDTO.class
                    );

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        refreshContracts();
                    } else {
                        // Show error animation or message
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // Show error animation or message
                }
            });
        });

        refreshBtn.setOnAction(e -> refreshContracts());
        logoutBtn.setOnAction(e -> {
            try {
                Preferences.userNodeForPackage(LegalContractDigitizerApplication.class).clear();
            } catch (BackingStoreException ex) {
                log.error(ex.getLocalizedMessage());
            }

            Platform.runLater(() -> {
                windowLauncher.launchWindow(new Stage(), "Legal contract digitizer - Login window", 500, 500, "/layout/login.fxml", Objects.requireNonNull(getClass().getResource("/static/style/login.css")).toExternalForm(), applicationContext.getBean(LoginController.class));
                windowController.getCloseBtn().fire();
            });
        });
        refreshContracts(); // auto-load
    }

    private void setupActionColumn() {
        actionCol.setCellFactory(col -> new TableCell<DigitalizedContract, Void>() {
            private final Button nextStepBtn = new Button();
            private final Button viewClausesBtn = new Button("View Clauses");
            private final Button viewSolidityBtn = new Button("View Solidity");

            private final HBox container = new HBox(8);

            {
                // Add CSS style classes
                nextStepBtn.getStyleClass().add("btn-action");
                viewClausesBtn.getStyleClass().add("btn-info");
                viewSolidityBtn.getStyleClass().add("btn-info");
                container.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                DigitalizedContract contract = getTableView().getItems().get(getIndex());
                ContractStatus status = contract.status();

                container.getChildren().clear();

                nextStepBtn.setOnAction(e -> performNextStep(contract));

                viewClausesBtn.setOnAction(e -> fetchAndShowClauses(contract));

                viewSolidityBtn.setOnAction(e -> fetchAndShowSolidity(contract));

                switch (status) {
                    case UPLOADED -> {
                        nextStepBtn.setText("Extract Clauses");
                        container.getChildren().add(nextStepBtn);
                    }
                    case CLAUSES_EXTRACTED, SOLIDITY_PREPARED -> {
                        nextStepBtn.setText("Generate Solidity");
                        container.getChildren().add(nextStepBtn);
                    }
                    case SOLIDITY_GENERATED,
                         DEPLOYED,
                         CONFIRMED -> {
                        nextStepBtn.setText("Ethereum Actions");
                        nextStepBtn.getStyleClass().add("btn-action");
                        nextStepBtn.setOnAction(e -> {
                            EthereumActionsController controller = applicationContext.getBean(EthereumActionsController.class);
                            controller.setContract(contract);
                            windowLauncher.launchWindow(
                                    new Stage(),
                                    "Ethereum Actions - " + contract.id(),
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

                if (status.compareTo(CLAUSES_EXTRACTED) >= 0) {
                    container.getChildren().add(viewClausesBtn);
                }
                if (status.compareTo(SOLIDITY_PREPARED) >= 0) {
                    container.getChildren().add(viewSolidityBtn);
                }

                if (!status.equals(CONFIRMED)) {
                    Button deleteBtn = new Button("Delete");
                    deleteBtn.getStyleClass().add("btn-danger");
                    deleteBtn.setOnAction(e -> {
                        String contractId = contract.id();
                        String url = baseUrl + "/" + contractId;
                        try {
                            ResponseEntity<Void> response = HttpClientUtil.delete(url, null, Void.class);
                            if (response.getStatusCode().is2xxSuccessful()) {
                                contractsTable.getItems().remove(contract);
                                refreshContracts();
                            } else {
                                String msg = response.getStatusCode() == HttpStatus.CONFLICT
                                        ? "Cannot delete: contract is already confirmed."
                                        : "Deletion failed: " + response.getStatusCode();
                                // Optionally show error UI:
                                // showError(msg);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            // Optionally show error UI:
                            // showError("Deletion failed due to exception");
                        }
                    });
                    container.getChildren().add(deleteBtn);
                }

                setGraphic(container);
            }
        });
    }

    private void setupTable() {
        idCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().id()));
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status().toString()));

        setupActionColumn();

        contractsTable.setRowFactory(tv -> new TableRow<DigitalizedContract>() {
            @Override
            protected void updateItem(DigitalizedContract item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    getStyleClass().removeAll("UPLOADED", "CLAUSES_EXTRACTED", "SOLIDITY_GENERATED", "DEPLOYED", "CONFIRMED");
                    setStyle("");
                } else {
                    // Remove old status classes to avoid accumulation
                    getStyleClass().removeAll("UPLOADED", "CLAUSES_EXTRACTED", "SOLIDITY_GENERATED", "DEPLOYED", "CONFIRMED");

                    // Add class matching the current status
                    String statusClass = item.status().toString();
                    if (statusClass != null) {
                        getStyleClass().add(statusClass);
                    }
                }
            }
        });
    }

    private void refreshContracts() {
        String userId = userData.get("userId");
        if (userId == null) return;

        String url = baseUrl + "/list?userId=" + userId;

        try {
            ResponseEntity<DigitalizedContract[]> response = HttpClientUtil.get(url, null, DigitalizedContract[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                DigitalizedContract[] contracts = response.getBody();

                Platform.runLater(() -> contractsTable.getItems().setAll(contracts));
            } else {
                log.error("Failed to load contracts: HTTP " + response.getStatusCode());
            }
        } catch (IOException e) {
            log.error("Error refreshing contracts", e);
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
            ResponseEntity<Void> response = HttpClientUtil.patch(url, null, null, Void.class);

            if (response.getStatusCode().is2xxSuccessful() || contract.status().equals(CLAUSES_EXTRACTED)) {
                refreshContracts();
            } else {
                System.err.println("Next step failed: HTTP " + response.getStatusCode());
                // showError("Next step failed: HTTP " + response.getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
            // showError("Next step failed due to exception");
        }
    }

    private void fetchAndShowClauses(DigitalizedContract contract) {
        List<String> clauses = contract.extractedClauses();
        if (clauses == null || clauses.isEmpty()) {
            log.warn("No clauses available for contract {}", contract.id());
        } else {
            ClausesViewController controller = applicationContext.getBean(ClausesViewController.class);
            controller.setClauses(clauses);

            windowLauncher.launchWindow(
                    new Stage(),
                    "Extracted legal clauses - " + contract.id(),
                    600,
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
        } else {
            WindowPreviewController controller = applicationContext.getBean(WindowPreviewController.class);
            controller.setText(soliditySource);

            windowLauncher.launchWindow(
                    new Stage(),
                    "Solidity contract - " + contract.id(),
                    800,
                    800,
                    "/layout/window_preview.fxml",
                    Objects.requireNonNull(getClass().getResource("/static/style/window_preview.css")).toExternalForm(),
                    controller
            );
        }
    }
}