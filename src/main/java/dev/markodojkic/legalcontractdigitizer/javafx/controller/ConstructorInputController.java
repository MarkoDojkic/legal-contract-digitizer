package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
    private final List<TextField> paramFields = new ArrayList<>();
    private List<Object> collectedParams = null;

    public void loadConstructorInputs(String abiJson) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<JsonObject>>() {}.getType();
        List<JsonObject> abiList = gson.fromJson(abiJson, listType);

        for (JsonObject item : abiList) {
            if ("constructor".equals(item.get("type").getAsString())) {
                JsonArray inputs = item.getAsJsonArray("inputs");

                for (JsonElement inputElem : inputs) {
                    JsonObject param = inputElem.getAsJsonObject();
                    String type = param.get("type").getAsString();
                    String name = param.get("name").getAsString();

                    Label label = new Label(name + " (" + type + ")");
                    TextField field = new TextField();
                    field.setPromptText("Enter " + name + " (" + type + ")");

                    paramTypes.add(type);
                    paramFields.add(field);

                    VBox fieldBox = new VBox(label, field);
                    fieldBox.setSpacing(4);
                    dynamicFieldsBox.getChildren().add(fieldBox);
                }
                break;
            }
        }
    }

    @FXML
    private void onConfirm() {
        try {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < paramTypes.size(); i++) {
                String type = paramTypes.get(i);
                String rawValue = paramFields.get(i).getText().trim();
                result.add(parseParam(type, rawValue));
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
            case "address" -> value; // validate address format if needed
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
