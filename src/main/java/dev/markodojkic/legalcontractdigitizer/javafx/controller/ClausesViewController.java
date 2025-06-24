package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClausesViewController implements WindowAwareController {
	@Setter
	@Getter
	private JavaFXWindowController windowController;

	@FXML
	private ListView<String> clausesListView;

	private List<String> pendingClauses;

	@FXML
	public void initialize() {
		if (pendingClauses != null) {
			clausesListView.getItems().setAll(pendingClauses);
		}
	}

	public void setClauses(List<String> clauses) {
		pendingClauses = clauses;
	}
}