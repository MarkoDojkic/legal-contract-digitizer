package dev.markodojkic.legalcontractdigitizer.model;

import javafx.scene.paint.Color;
import lombok.Getter;

public enum SpecialBackgroundType {
	ERROR(0, Color.color(101.0 / 255.0, 67.0 / 255.0, 68.0 / 255.0)),
	SUCCESS(51, Color.color(74.0 / 255.0, 105.0 / 255.0, 90.0 / 255.0)),
	WARN(103, Color.color(165.0 / 255.0, 162.0 / 255.0, 74.0 / 255.0)),
	INFO(154, Color.color(57.0 / 255.0, 88.0 / 255.0, 107.0 / 255.0)),
	HELP(205, Color.color(79.0 / 255.0, 73.0 / 255.0, 96.0 / 255.0));

	@Getter
	final int offsetX;
	@Getter
	final Color textColor;

	SpecialBackgroundType(int offsetX, Color textColor) {
		this.offsetX = offsetX;
		this.textColor = textColor;
	}
}