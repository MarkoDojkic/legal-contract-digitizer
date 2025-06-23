package dev.markodojkic.legalcontractdigitizer.javafx.uiController;

import dev.markodojkic.legalcontractdigitizer.LegalContractDigitizerApplication;
import dev.markodojkic.legalcontractdigitizer.dto.UploadResponseDTO;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.ContractStatus;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
    @FXML private TableColumn<DigitalizedContract, Void> deploymentAddressCol;
    @FXML private TableColumn<DigitalizedContract, String> statusCol;

    private final RestTemplate restTemplate = new RestTemplate();
    private final WindowLauncher windowLauncher;
    private final ApplicationContext applicationContext;
    private String baseUrl, etherscanUrl;

    @Setter
    private Map<String, String> userData;

    public MainController(@Value("${server.port}") Integer serverPort,
                             @Value("${ethereum.etherscan.url}") String etherscanUrl,
                             @Autowired WindowLauncher windowLauncher,
                             @Autowired ApplicationContext applicationContext){
        this.baseUrl = String.format("http://localhost:%s/api/v1/contracts", serverPort);
        this.etherscanUrl = etherscanUrl;
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
                CompletableFuture.runAsync(() -> {
                    try {
                        String url = baseUrl + "/upload";

                        // Create headers with auth (you can customize this method)
                        HttpHeaders headers = createHeadersWithAuth();

                        // Prepare multipart body
                        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                        body.add("file", new FileSystemResource(file));

                        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                        ResponseEntity<UploadResponseDTO> response = restTemplate.exchange(
                                url,
                                HttpMethod.POST,
                                requestEntity,
                                UploadResponseDTO.class
                        );

                        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                            String message = response.getBody().getMessage();
                            Platform.runLater(() -> {
//                                windowLauncher.launchSuccessAnimationWindow(new Stage());
                                refreshContracts();
                            });
                        } else {
                            Platform.runLater(() -> {
//                                windowLauncher.launchErrorAnimationWindow(new Stage());
                            });
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
//                        Platform.runLater(() -> windowLauncher.launchErrorAnimationWindow(new Stage()));
                    }
                });
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

    private HttpHeaders createHeadersWithAuth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userData.getOrDefault("idToken", "N/A"));
        return headers;
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
                    case CLAUSES_EXTRACTED -> {
                        nextStepBtn.setText("Generate Solidity");
                        container.getChildren().add(nextStepBtn);
                    }
                    case SOLIDITY_GENERATED -> {
                        nextStepBtn.setText("Deploy Contract");
                        container.getChildren().add(nextStepBtn);
                    }
                    case DEPLOYED -> {
                        nextStepBtn.setText("Check Confirmation");
                        container.getChildren().add(nextStepBtn);
                    }
                }

                if (status.compareTo(CLAUSES_EXTRACTED) >= 0) {
                    container.getChildren().add(viewClausesBtn);
                }
                if (status.compareTo(SOLIDITY_GENERATED) >= 0) {
                    container.getChildren().add(viewSolidityBtn);
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

        deploymentAddressCol.setCellFactory(col -> new TableCell<DigitalizedContract, Void>() {
            private final Label addressLabel = new Label();
            private final Button viewButton = new Button("View on Blockchain");
            private final HBox container = new HBox(8);

            {
                container.setAlignment(Pos.CENTER);
                viewButton.getStyleClass().add("btn-link");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                DigitalizedContract contract = getTableView().getItems().get(getIndex());
                String address = contract.deployedAddress();
                if (address != null && !address.isBlank()) {
                    addressLabel.setText(address);
                    viewButton.setOnAction(e -> windowLauncher.launchWebViewWindow(
                            new Stage(),
                            "Smart Contract view on Blockchain - " + contract.id(),
                            600,
                            600,
                            String.format("%s/address/%s", etherscanUrl, address)
                    ));
                    container.getChildren().setAll(addressLabel, viewButton);
                    setGraphic(container);
                } else {
                    setGraphic(null);
                }
            }
        });

    }

    private void refreshContracts() {
        String userId = userData.get("userId");
        if (userId == null) return;

        String url = baseUrl + "/list?userId=" + userId;
        try {
            HttpEntity<Void> entity = new HttpEntity<>(createHeadersWithAuth());
            ResponseEntity<DigitalizedContract[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    DigitalizedContract[].class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                DigitalizedContract[] contracts = response.getBody();
                Platform.runLater(() -> contractsTable.getItems().setAll(contracts));
            } else {
                log.error("Failed to load contracts: HTTP " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("HTTP error refreshing contracts: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
        }
    }

    // Example implementations of REST calls for buttons
    private void performNextStep(DigitalizedContract contract) {
        try {
            String url;
            HttpEntity<?> requestEntity;
            ResponseEntity<String> response = null;

            switch (contract.status()) {
                case UPLOADED:
                    url = baseUrl + "/extract-clauses?contractId=" + contract.id();
                    requestEntity = new HttpEntity<>(createHeadersWithAuth());
                    response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
                    break;

                case CLAUSES_EXTRACTED:
                    url = baseUrl + "/generate-solidity?contractId=" + contract.id();
                    requestEntity = new HttpEntity<>(createHeadersWithAuth());
                    response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
                    break;

                case SOLIDITY_GENERATED:
                    url = baseUrl + "/deploy-contract";
                    // Example DeploymentRequestDTO with empty constructor params:
                    Map<String, Object> body = Map.of("contractId", contract.id(), "constructorParams", List.of());
                    requestEntity = new HttpEntity<>(body, createHeadersWithAuth());
                    response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
                    break;

                case DEPLOYED:
                    //TODO: Check on blockchain if contract is confirmed
                    break;

                default:
                    return; // No action for CONFIRMED or unknown
            }

            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                refreshContracts();
            } else {
                System.err.println("Next step failed: " + (response != null ? response.getStatusCode() : "no response"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fetchAndShowClauses(DigitalizedContract contract) {
        List<String> clauses = contract.extractedClauses();
        if (clauses == null || clauses.isEmpty()) {
            log.warn("No clauses available for contract {}", contract.id());
        } else
            Platform.runLater(() -> {
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
            });
    }


    private void fetchAndShowSolidity(DigitalizedContract contract) {
        String soliditySource = contract.soliditySource();
        if (soliditySource == null) {
            log.warn("Solidity source not available for contract {}", contract.id());
        } else
            Platform.runLater(() -> {
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
            });
    }
}
