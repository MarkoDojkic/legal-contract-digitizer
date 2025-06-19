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
public class DeploymentRequestDTO {
	private String contractId;
	private List<Object> constructorParams;
}
