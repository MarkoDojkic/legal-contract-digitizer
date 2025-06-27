package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.markodojkic.legalcontractdigitizer.dto.WalletInfo;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Component
public class ConstructorInputController implements WindowAwareController {
    @Getter
    @Setter
    private JavaFXWindowController windowController;

    @FXML
    private VBox dynamicFieldsBox;

    private final List<String> paramTypes = new ArrayList<>();
    private final List<ComboBox<WalletInfo>> paramFields = new ArrayList<>();
    private List<Object> collectedParams = null;

    @Setter
    private List<WalletInfo> walletInfos;

    public void loadConstructorInputs(String abiJson) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<JsonObject>>() {}.getType();
        List<JsonObject> abiList = gson.fromJson(abiJson, listType);

        // Always add deployer address as the first parameter (required)
        Label deployerLabel = new Label("Select deployer address");
        ComboBox<WalletInfo> deployerDropdown = new ComboBox<>();
        deployerDropdown.getItems().addAll(walletInfos);
        deployerDropdown.setPromptText("Choose deployer address");

        // Add deployer to the parameter list (dynamically inferred type)
        paramTypes.add("address");  // Since deployer is an address, we add it dynamically
        paramFields.add(deployerDropdown);

        VBox deployerFieldBox = new VBox(deployerLabel, deployerDropdown);
        deployerFieldBox.setSpacing(4);
        dynamicFieldsBox.getChildren().add(deployerFieldBox);

        // Now, process constructor inputs dynamically based on ABI
        for (JsonObject item : abiList) {
            if ("constructor".equals(item.get("type").getAsString())) {
                JsonArray inputs = item.getAsJsonArray("inputs");

                for (JsonElement inputElem : inputs) {
                    JsonObject param = inputElem.getAsJsonObject();
                    String name = param.get("name").getAsString();
                    String type = param.get("type").getAsString(); // Get the type dynamically from ABI

                    // Create label and ComboBox for wallet selection
                    Label label = new Label("Select wallet for " + name);

                    ComboBox<WalletInfo> walletDropdown = new ComboBox<>();
                    walletDropdown.getItems().addAll(walletInfos);
                    walletDropdown.setPromptText("Choose wallet");

                    paramTypes.add(type);  // Dynamically add the correct type for other params
                    paramFields.add(walletDropdown);

                    VBox fieldBox = new VBox(label, walletDropdown);
                    fieldBox.setSpacing(4);
                    dynamicFieldsBox.getChildren().add(fieldBox);
                }
                break; // Only process the first constructor in the ABI (if there are multiple)
            }
        }
    }

    @FXML
    private void onConfirm() {
        try {
            List<Object> result = new ArrayList<>();

            // Always add deployer address as the first parameter (selected by the user)
            String deployerAddress = paramFields.get(0).getValue().getAddress();
            result.add(parseParam(paramTypes.get(0), deployerAddress)); // Use dynamic type for deployer address

            // Collect other parameters selected by the user
            for (int i = 1; i < paramTypes.size(); i++) {
                String type = paramTypes.get(i);
                String address = paramFields.get(i).getValue().getAddress();
                result.add(parseParam(type, address));
            }

            collectedParams = result;

            windowController.getCloseBtn().fire();

        } catch (Exception e) {
            showError("Invalid input: " + e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        collectedParams = null;
        windowController.getCloseBtn().fire();
    }

    public List<Object> getConstructorParams() {
        return collectedParams;
    }

    private Object parseParam(String type, String value) {
        return switch (type) {
            case "address" -> value; // Address is treated as a string
            case "uint256", "uint", "int", "int256" -> new java.math.BigInteger(value);
            case "bool" -> Boolean.parseBoolean(value);
            case "string" -> value;
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.showAndWait();
    }
}