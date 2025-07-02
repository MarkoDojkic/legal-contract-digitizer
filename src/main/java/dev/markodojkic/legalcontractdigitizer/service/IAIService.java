package dev.markodojkic.legalcontractdigitizer.service;

import java.util.List;

public interface IAIService {
	List<String> extractClauses(String contractText) throws Exception;
	String generateSolidityContract(List<String> clauses) throws Exception;
}
