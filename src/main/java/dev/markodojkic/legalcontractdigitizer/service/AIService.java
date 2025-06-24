package dev.markodojkic.legalcontractdigitizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

		// Specify the version to be 0.8.8 or higher
		promptBuilder.append("\nEnsure the Solidity version is `^0.8.8` or higher for compatibility with the latest Solidity compiler.\n");

		// Define contract creator as payable(msg.sender) directly, outside the constructor
		promptBuilder.append("\nAt least one payable, i.e. the contract creator or mediator should be defined as `address payable` and initialized as `payable(msg.sender)` but **should not** be included in the constructor. Other participants should be specified for the user to populate when deploying the contract to the blockchain.\n");

		// Ensure that monetary values are expressed in Ether
		promptBuilder.append("\nAll monetary values (totalAmount, payments, etc.) should be in Ether (use 'ether' unit).\n");

		// If self-destruction is allowed, include the selfdestruct function
		if (allowSelfDestruction) {
			promptBuilder.append("\nThe contract should include a method to remove the contract from the blockchain using `selfdestruct(address payable recipient)`.\n");
		}

		// Provide guidelines to the AI about what the contract should include
		promptBuilder.append("\nThe contract should include the necessary functions such as:\n");
		promptBuilder.append("- Payments between parties\n");
		promptBuilder.append("- Confirmations (e.g., service provided)\n");
		promptBuilder.append("- Contract termination functionality\n");
		promptBuilder.append("- Optionally, include penalty logic if defined in clauses.\n");

		// Request the Solidity code without markdown or backticks
		promptBuilder.append("\nReturn ONLY the Solidity code without markdown or backticks. Ensure the Solidity contract follows the clauses accurately, without including any unnecessary logic or modifications.\n");

		String prompt = promptBuilder.toString();

		try {
			// Send the prompt to the AI and get the generated Solidity code
			String rawSolidity = sendChatRequest(prompt);
			return sanitizeSolidityCode(rawSolidity);  // Assuming sanitizeSolidityCode is a method for cleaning up output
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
				if (choices.isArray() && choices.size() > 0) {
					return choices.get(0).path("message").path("content").asText();
				} else {
					throw new Exception("Invalid response format");
				}
			} catch (WebClientResponseException e) {
				// Handling OpenAI API errors
				log.error("OpenAI API error, HTTP status: {}, Body: {}", e.getRawStatusCode(), e.getResponseBodyAsString());
				if (e.getRawStatusCode() == 429) {
					// Rate limit reached, retry after some delay
					log.warn("Rate limit exceeded, retrying...");
				}
				// Retry on server errors (5xx) or rate limit (429)
				if (e.getRawStatusCode() >= 500 || e.getRawStatusCode() == 429) {
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
			throw new Exception("Error parsing extracted clauses.");
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