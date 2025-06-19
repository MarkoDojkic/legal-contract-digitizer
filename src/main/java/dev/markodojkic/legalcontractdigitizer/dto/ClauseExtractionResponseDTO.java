package dev.markodojkic.legalcontractdigitizer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClauseExtractionResponseDTO {
	@NotBlank(message = "Contract text must not be blank")
	private List<String> clauses;
}
