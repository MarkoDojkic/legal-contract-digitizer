package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.markodojkic.legalcontractdigitizer.dto.WalletInfo;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class ConstructorInputController extends WindowAwareController {
    public static final String ADDRESS = "address";

    @FXML private VBox dynamicFieldsBox;
    @FXML private Label titleLabel;

    @Setter
    private List<WalletInfo> walletInfos;

    private final List<String> paramTypes;
    private final List<Control> paramFields;
    private final List<Object> collectedParams;

    @Autowired
    public ConstructorInputController(WindowLauncher windowLauncher, ApplicationContext applicationContext) {
        super(windowLauncher, applicationContext);
        paramTypes = new ArrayList<>();
        paramFields = new ArrayList<>();
        collectedParams = new ArrayList<>();
    }

    public void loadParamInputs(String abiJson, String targetNameOrType, boolean isConstructor) {
        dynamicFieldsBox.getChildren().clear();
        paramTypes.clear();
        paramFields.clear();

        if (titleLabel != null) {
            titleLabel.setText(isConstructor ? "Enter Constructor Parameters" : "Enter Function Parameters");
        }

        Gson gson = new Gson();
        Type listType = new TypeToken<List<JsonObject>>() {}.getType();
        List<JsonObject> abiList = gson.fromJson(abiJson, listType);

        JsonObject target = null;
        for (JsonObject item : abiList) {
            if (isConstructor && "constructor".equals(item.get("type").getAsString())) {
                target = item;
                break;
            }
            if (!isConstructor && "function".equals(item.get("type").getAsString())
                    && item.has("name") && targetNameOrType.equals(item.get("name").getAsString())) {
                target = item;
                break;
            }
        }
        if (target == null) return;

        JsonArray inputs = target.has("inputs") ? target.getAsJsonArray("inputs") : new JsonArray();

        // Add deployer for constructor
        if (isConstructor && walletInfos != null) {
            Label deployerLabel = new Label("Select deployer address");
            ComboBox<WalletInfo> deployerDropdown = new ComboBox<>();
            deployerDropdown.getItems().addAll(walletInfos);
            deployerDropdown.setPromptText("Choose deployer address");

            paramTypes.add(ADDRESS);
            paramFields.add(deployerDropdown);
            dynamicFieldsBox.getChildren().add(createLabeledField(deployerLabel, deployerDropdown));
        } else {
            // For functions, we need collect the caller's address
            Label callerLabel = new Label("Select caller address");
            ComboBox<WalletInfo> callerDropdown = new ComboBox<>();
            if (walletInfos != null && !walletInfos.isEmpty()) {
                callerDropdown.getItems().addAll(walletInfos);
                callerDropdown.setPromptText("Choose caller address");
            } else {
                callerDropdown.setPromptText("No wallets available");
            }
            paramTypes.add(ADDRESS);
            paramFields.add(callerDropdown);
            dynamicFieldsBox.getChildren().add(createLabeledField(callerLabel, callerDropdown));
        }

        for (JsonElement inputElem : inputs) {
            JsonObject param = inputElem.getAsJsonObject();
            String name = param.has("name") ? param.get("name").getAsString() : "";
            String type = param.has("type") ? param.get("type").getAsString() : "string";
            String labelText = (name.isEmpty() ? "Parameter" : name) + " (" + type + ")";

            Control inputControl;
            if (type.startsWith(ADDRESS) && walletInfos != null && !walletInfos.isEmpty()) {
                ComboBox<WalletInfo> walletDropdown = new ComboBox<>();
                walletDropdown.getItems().addAll(walletInfos);
                walletDropdown.setPromptText("Choose wallet");
                inputControl = walletDropdown;
            } else {
                TextField textField = new TextField();
                textField.setPromptText("Enter value");
                inputControl = textField;
            }

            paramTypes.add(type);
            paramFields.add(inputControl);
            dynamicFieldsBox.getChildren().add(createLabeledField(new Label(labelText), inputControl));
        }
    }

    private VBox createLabeledField(Label label, Control input) {
        VBox box = new VBox(label, input);
        box.setSpacing(4);
        return box;
    }

    @FXML
    private void onConfirm() {
        try {
            collectedParams.clear();
            for (int i = 0; i < paramTypes.size(); i++) {
                String type = paramTypes.get(i);
                Control field = paramFields.get(i);
                String value;

                if (field instanceof ComboBox<?> cb) {
                    Object selected = cb.getValue();
                    if (selected == null) throw new IllegalArgumentException("Missing selection for " + type);
                    value = (selected instanceof WalletInfo wi) ? wi.getAddress() : selected.toString();
                } else if (field instanceof TextField tf) {
                    value = tf.getText();
                    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing input for " + type);
                } else {
                    throw new IllegalArgumentException("Unknown input field type");
                }

                collectedParams.add(parseParam(type, value));
            }
            windowController.getCloseButton().fire();
        } catch (Exception e) {
            windowLauncher.launchErrorSpecialWindow(e.getLocalizedMessage());
        }
    }

    @FXML
    private void onCancel() {
        collectedParams.clear();
        windowController.getCloseButton().fire();
        windowLauncher.launchWarnSpecialWindow("Action canceled");
    }

    public List<Object> getParams() {
        return collectedParams;
    }

    private Object parseParam(String type, String value) {
        if (type.endsWith("[]")) {
            String baseType = type.replace("[]", "");
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .map(val -> parseParam(baseType, val))
                    .toList();
        }

        return switch (type) {
            case ADDRESS, "address payable" -> {
                if (!value.matches("^0x[a-fA-F0-9]{40}$")) {
                    throw new IllegalArgumentException("Invalid Ethereum address: " + value);
                }
                yield value;
            }
            case "uint256", "uint", "int", "int256" -> new BigInteger(value);
            case "bool" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Invalid boolean value: " + value);
                }
                yield Boolean.parseBoolean(value);
            }
	        default -> value;
        };
    }
}