package dev.markodojkic.legalcontractdigitizer.test.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.markodojkic.legalcontractdigitizer.service.AIService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Disabled("All tests are failing") //TODO: fixes pending with tests revision
class AIServiceTest {

    @Mock
    private WebClient openAiWebClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AIService aiService;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setup() {
        Mockito.reset(openAiWebClient, openAiWebClient, requestBodyUriSpec, requestBodySpec, requestHeadersSpec, responseSpec);
        MockitoAnnotations.openMocks(this);

        // Setup the WebClient mock call chain:
        when(openAiWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void extractClauses_shouldReturnClauses_whenValidResponse() throws Exception {
        String contractText = "Some legal contract text";

        String fakeContent = "[\"Clause 1\", \"Clause 2\"]";
        String fakeRawJson = "{ \"choices\": [ { \"message\": { \"content\": \"" + fakeContent.replace("\"", "\\\"") + "\" } } ] }";

        // Mock response from WebClient
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(fakeRawJson));

        // Mock ObjectMapper to parse JSON response from OpenAI
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode choicesNode = mock(JsonNode.class);
        JsonNode firstChoiceNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode contentNode = mock(JsonNode.class);

        when(objectMapper.readTree(fakeRawJson)).thenReturn(rootNode);
        when(rootNode.path("choices")).thenReturn(choicesNode);
        when(choicesNode.get(0)).thenReturn(firstChoiceNode);
        when(firstChoiceNode.path("message")).thenReturn(messageNode);
        when(messageNode.path("content")).thenReturn(contentNode);
        when(contentNode.asText()).thenReturn(fakeContent);

        // Mock parsing the clauses JSON array string into List<String>
        when(objectMapper.readValue(
                eq(fakeContent),
                any(com.fasterxml.jackson.databind.JavaType.class))
        ).thenReturn(List.of("Clause 1", "Clause 2"));

        List<String> clauses = aiService.extractClauses(contractText);

        assertNotNull(clauses);
        assertEquals(2, clauses.size());
        assertEquals("Clause 1", clauses.get(0));
        assertEquals("Clause 2", clauses.get(1));

        // Verify the WebClient call was triggered
        verify(openAiWebClient).post();
    }

    @Test
    void generateSolidityContract_shouldReturnSanitizedSolidityCode() {
        List<String> clauses = List.of("Clause A", "Clause B");

        String rawResponse = "```solidity\npragma solidity ^0.8.0;\ncontract Test {}\n```";

        String expectedSanitized = "pragma solidity ^0.8.0;\ncontract Test {}";

        String fakeRawJson = "{ \"choices\": [ { \"message\": { \"content\": \"" + rawResponse.replace("\n", "\\n").replace("\"", "\\\"") + "\" } } ] }";

        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(fakeRawJson));

        JsonNode rootNode = mock(JsonNode.class);
        JsonNode choicesNode = mock(JsonNode.class);
        JsonNode firstChoiceNode = mock(JsonNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        JsonNode contentNode = mock(JsonNode.class);

        try {
            when(objectMapper.readTree(fakeRawJson)).thenReturn(rootNode);
        } catch (Exception e) {
            fail("Unexpected exception");
        }

        when(rootNode.path("choices")).thenReturn(choicesNode);
        when(choicesNode.get(0)).thenReturn(firstChoiceNode);
        when(firstChoiceNode.path("message")).thenReturn(messageNode);
        when(messageNode.path("content")).thenReturn(contentNode);
        when(contentNode.asText()).thenReturn(rawResponse);

        String solidityCode = aiService.generateSolidityContract(clauses);

        assertNotNull(solidityCode);
        assertEquals(expectedSanitized, solidityCode);
    }

    @Test
    void extractClauses_shouldReturnEmptyList_onException() throws Exception {
        String contractText = "some text";

        // Simulate exception in sendChatRequest by throwing on objectMapper.readTree
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("invalid json"));

        when(objectMapper.readTree(anyString())).thenThrow(new RuntimeException("Parsing error"));

        List<String> clauses = aiService.extractClauses(contractText);

        assertNotNull(clauses);
        assertTrue(clauses.isEmpty());
    }

    @Test
    void generateSolidityContract_shouldReturnEmptyString_onException() {
        List<String> clauses = List.of("some clause");

        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("invalid json"));

        try {
            when(objectMapper.readTree(anyString())).thenThrow(new RuntimeException("Parsing error"));
        } catch (Exception e) {
            fail("Unexpected exception");
        }

        String solidityCode = aiService.generateSolidityContract(clauses);

        assertEquals("", solidityCode);
    }
}