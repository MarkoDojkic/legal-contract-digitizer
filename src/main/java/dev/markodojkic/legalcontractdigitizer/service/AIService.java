package dev.markodojkic.legalcontractdigitizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.markodojkic.legalcontractdigitizer.exception.ClausesExtractionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

	private final WebClient openAiWebClient;
	private final ObjectMapper objectMapper;

	private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
	private static final String MODEL = "gpt-4-0613";
	private static final int RETRY_LIMIT = 3;

	public List<String> extractClauses(String contractText) {
		String prompt = """
                Extract all legal clauses from this legal contract text.
                Return the result as a **JSON array** of clauses (strings).
                Do not explain, do not include anything else but the array.

                %s
                """.formatted(contractText);

		try {
			// sendChatRequest returns just the content text, which should be a JSON array string
			String content = sendChatRequest(prompt);
			return parseExtractedClauses(content);
		} catch (Exception e) {
			log.error("Failed to extract clauses from contract", e);
			return Collections.emptyList();
		}
	}

	public String generateSolidityContract(List<String> clauses, boolean allowSelfDestruction) {
		StringBuilder promptBuilder = new StringBuilder();
		promptBuilder.append("Generate a Solidity smart contract based on the following clauses:\n\n");

		// Add each clause from the list to the prompt
		for (String clause : clauses) {
			promptBuilder.append("- ").append(clause).append("\n");
		}

		// Solidity version
		promptBuilder.append("\nEnsure the Solidity version is `^0.8.8` or higher for compatibility with the latest Solidity compiler.\n");

		// Define payable creator
		promptBuilder.append("\nDefine at least one payable address (e.g., the contract creator or mediator) initialized as `address payable` with `payable(msg.sender)`, but do not include it as a constructor argument.\n");

		// Monetary values in Ether
		promptBuilder.append("\nExpress all monetary values in Ether units (e.g., `1 ether`).\n");

		// Contract safety and architecture
		promptBuilder.append("\nThe contract must include the following best practices and features:\n");
		promptBuilder.append("- Separate contract termination logic from core business functions.\n");
		promptBuilder.append("- Prevent multiple executions of the same state-changing functions under the same conditions.\n");
		promptBuilder.append("- Use access control modifiers to restrict function execution.\n");
		promptBuilder.append("- Log important state changes with events.\n");
		promptBuilder.append("- Correctly handle payments with necessary checks.\n");

		// Always include a normal selfdestruct function
		promptBuilder.append("\nInclude a normal contract cleanup function that allows self-destruction after contract completion or termination conditions are met.\n");
		promptBuilder.append("This function should transfer remaining balance to an appropriate party (e.g., the deployer or client).\n");
		promptBuilder.append("Access to this cleanup function should be properly restricted.\n");

		// Optional forced selfdestruct for emergency
		if (allowSelfDestruction) {
			promptBuilder.append("\nAdditionally, include a forced selfdestruct function callable only by the deployer at any time to remove the contract and transfer funds to the deployer.\n");
		}

		// Expected functions
		promptBuilder.append("\nThe contract should include necessary functions such as:\n");
		promptBuilder.append("- Payments between parties\n");
		promptBuilder.append("- Confirmations or acknowledgments of services or conditions\n");
		promptBuilder.append("- Contract termination and cleanup functionality\n");
		promptBuilder.append("- Optional penalty logic if specified in the clauses.\n");

		// Only Solidity code no markdown/backticks
		promptBuilder.append("\nReturn ONLY the Solidity code without markdown or backticks. Ensure the code follows the clauses and includes described safety and destruct patterns.\n");

		String prompt = promptBuilder.toString();

		try {
			String rawSolidity = sendChatRequest(prompt);
			return sanitizeSolidityCode(rawSolidity);
		} catch (Exception e) {
			log.error("Failed to generate Solidity contract", e);
			return "";
		}
	}


	/**
	 * Sends prompt to OpenAI Chat API and returns the "content" text from choices[0].message
	 */
	private String sendChatRequest(String prompt) {
		String rawJson = null;

		// Retry logic
		for (int i = 0; i < RETRY_LIMIT; i++) {
			try {
				rawJson = openAiWebClient.post()
						.uri(OPENAI_API_URL)
						.bodyValue(Map.of(
								"model", MODEL,
								"messages", List.of(Map.of(
										"role", "user",
										"content", prompt
								)),
								"temperature", 0.68
						))
						.retrieve()
						.bodyToMono(String.class)
						.block();

				// Log the response
				log.debug("OpenAI response (attempt {}): {}", i + 1, rawJson);

				JsonNode root = objectMapper.readTree(rawJson);
				JsonNode choices = root.path("choices");
				if (choices.isArray() && !choices.isEmpty()) {
					return choices.get(0).path("message").path("content").asText();
				} else {
					throw new ClausesExtractionException("Invalid response format", null);
				}
			} catch (WebClientResponseException e) {
				// Handling OpenAI API errors
				log.error("OpenAI API error, HTTP status: {}, Body: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
				if (e.getStatusCode().value() == 429) {
					// Rate limit reached, retry after some delay
					log.warn("Rate limit exceeded, retrying...");
				}
				// Retry on server errors (5xx) or rate limit (429)
				if (e.getStatusCode().value() >= 500 || e.getStatusCode().value() == 429) {
					try {
						Thread.sleep(1000); // Backoff before retrying
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
					continue;
				}
				throw e; // For non-retryable errors, throw exception
			} catch (Exception e) {
				log.error("Error processing OpenAI response", e);
				return "An error occurred while processing the AI request.";
			}
		}

		// If retries exhausted, log error
		log.error("Failed to get a valid response from OpenAI after {} attempts", RETRY_LIMIT);
		return "Failed to generate the Solidity contract after multiple attempts.";
	}

	/**
	 * Parses the JSON array string content into List<String> clauses
	 */
	private List<String> parseExtractedClauses(String jsonArrayString) throws Exception {
		try {
			return objectMapper.readValue(
					jsonArrayString,
					objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
			);
		} catch (Exception e) {
			log.error("Failed to parse clauses JSON: {}", jsonArrayString, e);
			throw new ClausesExtractionException("Error parsing extracted clauses.", e.getCause());
		}
	}

	/**
	 * Removes markdown code fences if present and trims the output
	 */
	private String sanitizeSolidityCode(String rawCode) {
		if (rawCode == null) {
			return "No Solidity code generated.";
		}
		return rawCode
				.replaceAll("(?m)^```solidity\\s*", "")
				.replaceAll("(?m)^```\\s*", "")
				.trim();
	}
}