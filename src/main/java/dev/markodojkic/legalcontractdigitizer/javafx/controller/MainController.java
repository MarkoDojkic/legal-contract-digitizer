package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.common.reflect.TypeToken;
import dev.markodojkic.legalcontractdigitizer.LegalContractDigitizerApplication;
import dev.markodojkic.legalcontractdigitizer.model.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.model.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.AuthSession;
import dev.markodojkic.legalcontractdigitizer.util.Either;
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

import static dev.markodojkic.legalcontractdigitizer.model.ContractStatus.*;

@Component
@Slf4j
public class MainController extends WindowAwareController {
    @FXML private Label nameLabel, emailLabel;
    @FXML private Button uploadBtn, refreshBtn, logoutBtn, walletsManagerBtn;

    @FXML private TableView<DigitalizedContract> contractsTable;
    @FXML private TableColumn<DigitalizedContract, String> idCol, statusCol;
    @FXML private TableColumn<DigitalizedContract, Void> actionCol;

    private final Preferences preferences = Preferences.userNodeForPackage(LegalContractDigitizerApplication.class);
    private final HttpClientUtil httpClientUtil;
    private final String baseUrl;

    @Autowired
    public MainController(@Value("${server.port}") Integer serverPort, WindowLauncher windowLauncher, ApplicationContext applicationContext, HttpClientUtil httpClientUtil){
        super(windowLauncher, applicationContext);
        this.baseUrl = String.format("http://localhost:%s/api/v1/contracts", serverPort);
        this.httpClientUtil = httpClientUtil;
    }

    @FXML
    public void initialize() {
        nameLabel.setText("Logged in as: " + preferences.get("name", "N/A"));
        emailLabel.setText("Email:" + preferences.get("email", "N/A"));

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
                log.error("Cannot upload new contract", e);
                windowLauncher.launchErrorSpecialWindow("Error occurred while uploading new contract:\n" + e.getLocalizedMessage());
            }
        }));
        walletsManagerBtn.setOnAction(_ -> openWalletsManager());
        refreshBtn.setOnAction(_ -> refreshContracts());
        logoutBtn.setOnAction(_ -> {
            try {
               preferences.clear();
            } catch (BackingStoreException e) {
                log.error("Cannot clear user data", e);
                windowLauncher.launchWarnSpecialWindow("Error occurred while clearing user data:\n" + e.getLocalizedMessage());
            }

            AuthSession.logout();

            Platform.runLater(() -> {
                windowLauncher.launchWindow("Login window", 500, 500, "/layout/login.fxml", Objects.requireNonNull(getClass().getResource("/static/style/login.css")).toExternalForm(), applicationContext.getBean(LoginController.class));
                windowController.getCloseButton().fire();
                windowLauncher.launchSuccessSpecialWindow("User logged out");
            });
        });

        refreshContracts();
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

    private void setupTable() {
        idCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().id()));
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status().toString()));

        actionCol.setCellFactory(_ -> new TableCell<>() {
            private final Button nextStepBtn = new Button();
            private final Button viewClausesBtn = new Button("View Clauses ⚖️");
            private final Button viewEditSolidityBtn = new Button("View/Edit Solidity 📃");
            private final Button deleteBtn = new Button("Delete 🗑️");

            private final HBox container = new HBox(8);

            {
                nextStepBtn.getStyleClass().add("btn-action");
                viewClausesBtn.getStyleClass().add("btn-info");
                viewEditSolidityBtn.getStyleClass().add("btn-info");
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

                nextStepBtn.setOnAction(_ -> performNextStep(getTableView().getItems().get(getIndex())));

                viewClausesBtn.setOnAction(_ -> fetchAndShowClauses(getTableView().getItems().get(getIndex())));

                viewEditSolidityBtn.setOnAction(_ -> fetchAndShowSolidity(getTableView().getItems().get(getIndex())));

                deleteBtn.setOnAction(_ -> {
                    try {
                        ResponseEntity<String> response = httpClientUtil.delete(baseUrl + "/" + getTableView().getItems().get(getIndex()).id(), null, String.class);
                        if (response.getStatusCode().is2xxSuccessful()) {
                            contractsTable.getItems().remove(getTableView().getItems().get(getIndex()));
                            refreshContracts();
                        } else if (response.getStatusCode() == HttpStatus.CONFLICT)
                            windowLauncher.launchWarnSpecialWindow(response.getBody());
                        else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
                    } catch (Exception e) {
                        log.error("Cannot delete contract", e);
                        windowLauncher.launchErrorSpecialWindow("Deletion failed due to exception:\n" + e.getLocalizedMessage());
                    }
                });

                switch (getTableView().getItems().get(getIndex()).status()) {
                    case UPLOADED -> {
                        nextStepBtn.setText("Extract Clauses 🗨️➡️⚖️");
                        container.getChildren().add(nextStepBtn);
                    }
                    case CLAUSES_EXTRACTED -> {
                        nextStepBtn.setText("Prepare Solidity ⚖️➡️📄");
                        container.getChildren().add(nextStepBtn);
                    }
                    case SOLIDITY_PREPARED -> {
                        nextStepBtn.setText("Generate Solidity 📄➡️📃");
                        container.getChildren().add(nextStepBtn);
                    }
                    case SOLIDITY_GENERATED,
                         DEPLOYED,
                         CONFIRMED,
                         TERMINATED -> {
                        nextStepBtn.setText("Ethereum Actions 🕸️");
                        nextStepBtn.getStyleClass().add("btn-action");
                        nextStepBtn.setOnAction(_ -> {
                            EthereumActionsController controller = applicationContext.getBean(EthereumActionsController.class);
                            controller.setContract(getTableView().getItems().get(getIndex()));
                            controller.setMainRefreshBtn(refreshBtn);
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

                if (getTableView().getItems().get(getIndex()).status().compareTo(CLAUSES_EXTRACTED) >= 0)
                    container.getChildren().add(viewClausesBtn);
                if (getTableView().getItems().get(getIndex()).status().compareTo(SOLIDITY_PREPARED) >= 0)
                    container.getChildren().add(viewEditSolidityBtn);
                if (getTableView().getItems().get(getIndex()).status().compareTo(CONFIRMED) < 0)
                    container.getChildren().add(deleteBtn);

                setGraphic(container);
            }
        });

        contractsTable.setRowFactory(_ -> new TableRow<>() {
            @Override
            protected void updateItem(DigitalizedContract item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(Arrays.stream(values()).map(Enum::name).toList());
                if (!empty && item != null && item.status() != null) getStyleClass().add(item.status().toString());
            }
        });
    }

    private void refreshContracts() {
        try {
            ResponseEntity<Either<List<DigitalizedContract>, String>> response = httpClientUtil.get(baseUrl + "/list", null, new TypeToken<Either<List<DigitalizedContract>, String>>(){}.getType());

            if(response.getBody() == null) throw new NoHttpResponseException("Listing user contracts failed with no response");
            else if (response.getStatusCode().is2xxSuccessful()) Platform.runLater(() -> contractsTable.getItems().setAll(response.getBody().left()));
            else throw new HttpResponseException(response.getStatusCode().value(), response.getBody().right());
        } catch (Exception e) {
            log.error("Cannot retrieve list of contracts", e);
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

            if(response.getBody() == null) throw new NoHttpResponseException((contract.status() == UPLOADED ? "Clauses extraction" : "Action related to solidity code") + " failed with no response");
            else if (response.getStatusCode().is2xxSuccessful()) {
                refreshContracts();
                if(response.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(HttpStatus.PARTIAL_CONTENT.value()))) windowLauncher.launchWarnSpecialWindow(response.getBody());
                else windowLauncher.launchSuccessSpecialWindow(response.getBody());
            } else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
        } catch (Exception e) {
            log.error("Cannot invoke action upon uploaded contract", e);
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
            SolidityViewController controller = applicationContext.getBean(SolidityViewController.class);
            controller.setText(soliditySource);
            controller.setMainRefreshBtn(refreshBtn);
            controller.setContractId(contract.status().equals(ContractStatus.SOLIDITY_PREPARED) ? contract.id() : null);

            windowLauncher.launchWindow(
                    "Generated solidity code",
                    800,
                    800,
                    "/layout/solidity_view.fxml",
                    Objects.requireNonNull(getClass().getResource("/static/style/solidity_view.css")).toExternalForm(),
                    controller
            );
        }
    }
}