package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.dto.DeploymentStatusResponseDTO;

import java.util.List;

public interface IContractService {
	String saveUploadedContract(String contractText);
	List<String> extractClauses(String contractText);
	String generateSolidity(String contractId);
	String deployContractWithParams(String contractId, List<Object> constructorParams);
	DeploymentStatusResponseDTO getContractStatus(String contractId);
}