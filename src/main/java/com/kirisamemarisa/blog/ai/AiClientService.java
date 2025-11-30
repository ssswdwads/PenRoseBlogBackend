package com.kirisamemarisa.blog.ai;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AiClientService {
    private final AiProperties properties;
    private final RestTemplate restTemplate;

    public AiClientService(AiProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * Send a simple user message to the configured OpenAI-compatible chat endpoint and return the assistant reply.
     */
    public String chat(String userMessage) {
        String url = properties.getNormalizedApiBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = properties.getEffectiveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI api key is not configured. Set spring.ai.openai.api-key or set env var.");
        }
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", properties.getModelOrDefault(),
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );

        Map<String, Object> respBody = response.getBody();
        if (respBody == null) return null;

        // Try to extract assistant content from response structure similar to OpenAI
        Object choicesObj = respBody.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
            Object first = choices.get(0);
            if (first instanceof Map<?, ?> firstMap) {
                Object message = firstMap.get("message");
                if (message instanceof Map<?, ?> msgMap) {
                    Object content = msgMap.get("content");
                    if (content != null) return content.toString();
                }
                // older OpenAI responses put text directly
                Object text = firstMap.get("text");
                if (text != null) return text.toString();
            }
        }

        // Fallback: search for a 'message' in top-level keys
        if (respBody.containsKey("message")) {
            return String.valueOf(respBody.get("message"));
        }

        return respBody.toString();
    }
}
