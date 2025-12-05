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
     * Send a simple user message to the configured OpenAI-compatible chat endpoint
     * and return the assistant reply.
     */
    public String chat(String userMessage) {
        String url = properties.getNormalizedApiBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.add("User-Agent", "blog-ai-client/1.0");
        String apiKey = properties.getEffectiveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "AI api key is not configured. Set spring.ai.openai.api-key or set env var.");
        }
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", properties.getModelOrDefault(),
                "messages", List.of(Map.of("role", "user", "content", userMessage)));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response = null;
        RuntimeException lastEx = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        request,
                        new ParameterizedTypeReference<>() {
                        });
                lastEx = null;
                break;
            } catch (org.springframework.web.client.ResourceAccessException ex) {
                lastEx = ex;
                // quick backoff before a single retry
                try {
                    Thread.sleep(150L * attempt);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (lastEx != null)
            throw lastEx;

        Map<String, Object> respBody = response != null ? response.getBody() : null;
        if (respBody == null)
            return null;

        // Try to extract assistant content from response structure similar to OpenAI
        Object choicesObj = respBody.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
            Object first = choices.get(0);
            if (first instanceof Map<?, ?> firstMap) {
                Object message = firstMap.get("message");
                if (message instanceof Map<?, ?> msgMap) {
                    Object content = msgMap.get("content");
                    if (content != null)
                        return content.toString();
                }
                // older OpenAI responses put text directly
                Object text = firstMap.get("text");
                if (text != null)
                    return text.toString();
            }
        }

        // Fallback: search for a 'message' in top-level keys
        if (respBody.containsKey("message")) {
            return String.valueOf(respBody.get("message"));
        }

        return respBody.toString();
    }
}
