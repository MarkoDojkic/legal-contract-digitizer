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
	private static final String MODEL = "gpt-4o"; // <- Upgrade to latest model
	private static final int RETRY_LIMIT = 3;

	public List<String> extractClauses(String contractText) {
		String prompt = """
                Extract all legal clauses from this legal contract text.
                Return the result as a JSON array of clauses (strings).
                Do not explain, do not include anything else but the JSON array (e.g. ["Clause 1", "Clause 2"]). 
                Do not include any markdown formatting or code blocks nor ```json tags.
                Legal Contract Text:
                "%s"
                """.formatted(contractText);

		try {
			String content = sendChatRequest(prompt, true);
			return parseExtractedClauses(content);
		} catch (Exception e) {
			log.error("Failed to extract clauses from contract", e);
			return Collections.emptyList();
		}
	}

	public String generateSolidityContract(List<String> clauses) {
		StringBuilder promptBuilder = new StringBuilder();
		promptBuilder.append("Generate a Solidity smart contract based on the following clauses:\n\n");

		for (String clause : clauses) {
			promptBuilder.append("- ").append(clause).append("\n");
		}

		promptBuilder.append("\nRequirements:\n");
		promptBuilder.append("1. Use Solidity version ^0.8.8 or higher with MIT licence.\n");
		promptBuilder.append("2. Pass all parties involved in payments as constructor arguments. The contract deployer must have full admin and upgrade rights.\n");
		promptBuilder.append("3. Express all monetary values in Ether units (e.g., 1 ether).\n");
		promptBuilder.append("4. Implement a circuit breaker pattern with a boolean `destroyed` flag that disables all core business functions. Do NOT use `selfdestruct`.\n");
		promptBuilder.append("5. Keep contract termination/disable logic separated from core business logic.\n");
		promptBuilder.append("6. Ensure idempotency by preventing repeated execution of state-changing functions like payments or confirmations.\n");
		promptBuilder.append("7. Use access control modifiers to restrict function access to authorized roles only (owner, client, freelancer).\n");
		promptBuilder.append("8. Emit events for every major state change, payment made, confirmation, contract termination, and upgrade.\n");
		promptBuilder.append("9. Validate and safeguard all incoming payments: payments must be exact, only accepted from the correct parties, and immediately transferred to the correct recipient.\n");
		promptBuilder.append("10. Include a `terminateContract` function that sets `destroyed` to true and transfers remaining balance to the owner.\n");
		promptBuilder.append("11. Restrict `terminateContract` and emergency withdrawal functions to the owner only.\n");
		promptBuilder.append("12. Implement the UUPS (Universal Upgradeable Proxy Standard) pattern with an `upgradeTo` function that only the owner can call.\n");
		promptBuilder.append("13. Include functions to:\n");
		promptBuilder.append("    - Pay the upfront payment from client to freelancer immediately upon receipt, only once.\n");
		promptBuilder.append("    - Confirm service completion (only by client or freelancer as specified).\n");
		promptBuilder.append("    - Pay the remaining completion payment after confirmation, only once.\n");
		promptBuilder.append("    - Disable the contract using the circuit breaker pattern.\n");
		promptBuilder.append("    - Optionally handle penalties if specified in clauses.\n");
		promptBuilder.append("14. Use `ReentrancyGuard` or equivalent logic to protect all state-changing and payable functions from reentrancy.\n");
		promptBuilder.append("15. Implement an `emergencyWithdraw` function that allows the owner to withdraw all funds ONLY when the contract is disabled.\n");
		promptBuilder.append("16. Ensure payments are transferred immediately to recipients to avoid locked funds.\n");
		promptBuilder.append("17. Revert direct payments to the contract unless through explicit payment functions.\n");

		promptBuilder.append("\nReturn ONLY the complete, production-ready Solidity code. Inline all dependencies (e.g. OpenZeppelin's Ownable, UUPSUpgradeable, ReentrancyGuard) so that the contract is fully self-contained and has no imports. Do not include markdown, explanations, or code formatting symbols.\n");

		try {
			String rawSolidity = sendChatRequest(promptBuilder.toString(), false);
			return sanitizeSolidityCode(rawSolidity);
		} catch (Exception e) {
			log.error("Failed to generate Solidity contract", e);
			return "";
		}
	}

	private String sendChatRequest(String prompt, boolean isExtraction) {
		String rawJson = null;

		for (int i = 0; i < RETRY_LIMIT; i++) {
			try {
				rawJson = openAiWebClient.post()
						.uri(OPENAI_API_URL)
						.bodyValue(Map.of(
								"model", MODEL,
								"temperature", 0.68,
								"max_tokens", 2048,
								"messages", List.of(
										Map.of("role", "system", "content",
												isExtraction ? "You are a contract analyst. Extract and return only legal clauses." :
														"You are an expert Solidity smart contract generator. Output only production-ready code."),
										Map.of("role", "user", "content", prompt)
								)
						))
						.retrieve()
						.bodyToMono(String.class)
						.block();

				log.debug("OpenAI raw response (attempt {}): {}", i + 1, rawJson);

				JsonNode root = objectMapper.readTree(rawJson);
				JsonNode choices = root.path("choices");
				if (choices.isArray() && !choices.isEmpty()) {
					return choices.get(0).path("message").path("content").asText();
				} else {
					throw new IllegalStateException("Unexpected response format.");
				}

			} catch (WebClientResponseException e) {
				log.error("OpenAI API error: HTTP {}, body: {}", e.getStatusCode().value(), e.getResponseBodyAsString());

				if (e.getStatusCode().value() == 429 || e.getStatusCode().value() >= 500) {
					log.warn("Rate limit or server error, retrying after delay...");
					try {
						Thread.sleep(1000L);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
					continue;
				}
				throw e;

			} catch (Exception e) {
				log.error("General error while calling OpenAI API", e);
				throw new RuntimeException("OpenAI request failed", e);
			}
		}

		throw new RuntimeException("Failed to get valid OpenAI response after " + RETRY_LIMIT + " retries.");
	}

	private List<String> parseExtractedClauses(String jsonArrayString) throws Exception {
		try {
			return objectMapper.readValue(
					jsonArrayString,
					objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
			);
		} catch (Exception e) {
			log.error("Failed to parse clause list: {}", jsonArrayString, e);
			throw new ClausesExtractionException("Error parsing extracted clauses.", e);
		}
	}

	private String sanitizeSolidityCode(String rawCode) {
		if (rawCode == null) return "No code generated.";
		return rawCode
				.replaceAll("(?m)^```solidity\\s*", "")
				.replaceAll("(?m)^```\\s*", "")
				.replaceAll("(?m)^```\\s*$", "")
				.trim();
	}
}