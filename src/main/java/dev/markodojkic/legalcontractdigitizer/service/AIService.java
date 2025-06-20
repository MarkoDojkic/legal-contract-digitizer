package dev.markodojkic.legalcontractdigitizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

	private final WebClient openAiWebClient;
	private final ObjectMapper objectMapper;

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

	public String generateSolidityContract(List<String> clauses) {
		StringBuilder promptBuilder = new StringBuilder();
		promptBuilder.append("Generate a Solidity smart contract based on the following clauses:\n\n");
		for (String clause : clauses) {
			promptBuilder.append("- ").append(clause).append("\n");
		}
		promptBuilder.append("\nReturn ONLY the Solidity code without markdown or backticks.");

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
		String rawJson = openAiWebClient.post()
				.uri("https://api.openai.com/v1/chat/completions")
				.bodyValue(Map.of(
						"model", "gpt-4-0613",
						"messages", List.of(Map.of(
								"role", "user",
								"content", prompt
						)),
						"temperature", 0.5
				))
				.retrieve()
				.bodyToMono(String.class)
				.block();

		try {
			JsonNode root = objectMapper.readTree(rawJson);
			return root.path("choices").get(0).path("message").path("content").asText();
		} catch (Exception e) {
			log.error("Failed to parse AI response JSON", e);
			return "";
		}
	}

	/**
	 * Parses the JSON array string content into List<String> clauses
	 */
	private List<String> parseExtractedClauses(String jsonArrayString) throws Exception {
		return objectMapper.readValue(
				jsonArrayString,
				objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
		);
	}

	/**
	 * Removes markdown code fences if present and trims the output
	 */
	private String sanitizeSolidityCode(String rawCode) {
		return rawCode
				.replaceAll("(?m)^```solidity\\s*", "")
				.replaceAll("(?m)^```\\s*", "")
				.trim();
	}
}