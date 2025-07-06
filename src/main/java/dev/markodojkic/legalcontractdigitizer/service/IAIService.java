package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.exception.ClausesExtractionException;
import org.apache.hc.client5.http.impl.classic.RequestFailedException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
	 * @throws ClausesExtractionException if clause extraction fails
	 */
	List<String> extractClauses(String contractText) throws ClausesExtractionException;

	/**
	 * Generates Solidity contract code based on the provided clauses.
	 *
	 * @param clauses the list of clauses to convert into Solidity code
	 * @return the generated Solidity contract code as a string
	 * @throws WebClientResponseException if the web client call fails with an error response
	 * @throws RequestFailedException if the request fails unexpectedly
	 * @throws ConnectionRequestTimeoutException if the request times out
	 */
	String generateSolidityContract(List<String> clauses) throws WebClientResponseException, RequestFailedException, ConnectionRequestTimeoutException;
}