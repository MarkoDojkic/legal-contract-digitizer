package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ClausesViewController extends WindowAwareController {
	@FXML
	private ListView<String> clausesListView;

	private final List<String> pendingClauses;

	@Autowired
	public ClausesViewController(WindowLauncher windowLauncher, ApplicationContext applicationContext) {
		super(windowLauncher, applicationContext);
		this.pendingClauses = new ArrayList<>();
	}

	@FXML
	public void initialize() {
		clausesListView.getItems().setAll(pendingClauses);
	}

	public void addClauses(List<String> clauses) {
		pendingClauses.addAll(clauses);
	}
}