package dev.markodojkic.legalcontractdigitizer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIService {

	private final ChatModel chatModel;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public List<String> extractClauses(String contractText) {
		PromptTemplate request = PromptTemplate.builder()
				.template("""
                Extract all legal clauses from the following contract text.
                Return the result as a JSON array of clause strings only. Do not add any commentary.

                Contract text:
                ```{contractText}```
            """)
				.variables(Map.of("contractText", contractText))
				.build();

		ChatResponse response = chatModel.call(request.create());
		String rawOutput = response.getResult().getOutput().getText();
		log.info("AI extracted raw JSON: {}", rawOutput);

		try {
			return objectMapper.readValue(rawOutput, new TypeReference<>() {});
		} catch (Exception e) {
			log.error("Failed to parse AI JSON output", e);
			throw new RuntimeException("AI response was not valid JSON: " + rawOutput);
		}
	}
}
