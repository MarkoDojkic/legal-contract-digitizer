package dev.markodojkic.legalcontractdigitizer.service;

import java.util.List;

/**
 * Service interface for AI-related operations such as clause extraction and Solidity contract generation.
 */
public interface IAIService {

	/**
	 * Extracts clauses from the given contract text.
	 *
	 * @param contractText the raw text of the contract to analyze
	 * @return a list of extracted clauses as strings
	 * @throws Exception if clause extraction fails
	 */
	List<String> extractClauses(String contractText) throws Exception;

	/**
	 * Generates Solidity contract code based on the provided clauses.
	 *
	 * @param clauses list of clauses to convert into Solidity code
	 * @return Solidity contract code as a string
	 * @throws Exception if generation fails
	 */
	String generateSolidityContract(List<String> clauses) throws Exception;
}