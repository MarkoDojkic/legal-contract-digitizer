package dev.markodojkic.legalcontractdigitizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompilationResultDTO {
	private String bin;
	private String abi;
}
