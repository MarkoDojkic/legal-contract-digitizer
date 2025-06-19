package dev.markodojkic.legalcontractdigitizer.test.service;

import dev.markodojkic.legalcontractdigitizer.service.AIService;
import dev.markodojkic.legalcontractdigitizer.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIServiceTest {

    @InjectMocks
    private AIService aiService;

    @Mock
    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        Mockito.reset(chatModel);
        MockitoAnnotations.openMocks(this);
        TestUtils.setField(aiService, "chatModel", chatModel);
    }

    @Test
    void testExtractClauses() {
        String contractText = "Sample contract text";

        String jsonResponse = "[\"Clause 1\", \"Clause 2\"]";
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockAssistantMessage = mock(AssistantMessage.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(mockAssistantMessage);
        when(mockAssistantMessage.getText()).thenReturn(jsonResponse);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);
        List<String> expectedClauses = List.of("Clause 1", "Clause 2");
        PromptTemplate request = PromptTemplate.builder()
                .template("""
                            Extract all legal clauses from the following contract text.
                            Return the result as a JSON array of clause strings only. Do not add any commentary.

                            Contract text:
                            ```{contractText}```
                        """)
                .variables(Map.of("contractText", contractText))
                .build();
        when(chatModel.call(request.create())).thenReturn(mockResponse);
        List<String> clauses = aiService.extractClauses(contractText);
        assertEquals(expectedClauses, clauses);
        verify(chatModel).call(request.create());
        verify(mockResponse).getResult();
        verify(mockGeneration).getOutput();
        verify(mockAssistantMessage).getText();
        verifyNoMoreInteractions(chatModel, mockResponse, mockGeneration, mockAssistantMessage);
    }

    @Test
    void testExtractClausesWithInvalidJson() {
        String contractText = "Sample contract text";

        String invalidJsonResponse = "Invalid JSON response";
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockAssistantMessage = mock(AssistantMessage.class);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(mockAssistantMessage);
        when(mockAssistantMessage.getText()).thenReturn(invalidJsonResponse);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        try {
            aiService.extractClauses(contractText);
        } catch (RuntimeException e) {
            assertEquals("AI response was not valid JSON: " + invalidJsonResponse, e.getMessage());
        }

        verify(chatModel).call(any(Prompt.class));
        verify(mockResponse).getResult();
        verify(mockGeneration).getOutput();
        verify(mockAssistantMessage).getText();
        verifyNoMoreInteractions(chatModel, mockResponse, mockGeneration, mockAssistantMessage);
    }
}
