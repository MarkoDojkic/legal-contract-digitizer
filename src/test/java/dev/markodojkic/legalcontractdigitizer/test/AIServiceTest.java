/*
package dev.markodojkic.legalcontractdigitizer.test;

import dev.markodojkic.legalcontractdigitizer.service.AIService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AIServiceTest {

	private ChatModel chatModel;
	private AIService aiService;

	@BeforeEach
	void setup() {
		chatModel = mock(ChatModel.class);
		aiService = new AIService(chatModel);
	}

	@Test
	void extractClauses_shouldExtractCorrectly() {
		String contract = "Clause 1: Test\nClause 2: Another";
		String rawResponse = "- Clause A\n- Clause B";

		ChatResponse response = mock(ChatResponse.class);
		ChatResult result = mock(ChatResult.class);
		ResponseMessage msg = new ResponseMessage(rawResponse);
		when(chatModel.call(any())).thenReturn(response);
		when(response.getResult()).thenReturn(result);
		when(result.getOutput()).thenReturn(msg);

		List<String> clauses = aiService.extractClauses(contract);

		assertEquals(2, clauses.size());
		assertEquals("Clause A", clauses.get(0));
	}
}
*/
