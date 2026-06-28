package com.zerohour.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    @Autowired
    private OkHttpClient baseClient;

    private OkHttpClient client;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        this.client = baseClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build();
    }

    private static final String GEMINI_API_URL = 
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    public String generateContent(String prompt) {
        log.info("Sending content generation request to Gemini...");
        try {
            // Build the body JSON structure using Jackson
            ObjectNode rootNode = objectMapper.createObjectNode();
            
            // "contents": [{"parts": [{"text": prompt}]}]
            ObjectNode partNode = objectMapper.createObjectNode().put("text", prompt);
            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.putArray("parts").add(partNode);
            rootNode.putArray("contents").add(contentNode);

            // "generationConfig": {"responseMimeType": "application/json"}
            ObjectNode genConfig = objectMapper.createObjectNode();
            genConfig.put("responseMimeType", "application/json");
            rootNode.set("generationConfig", genConfig);

            String requestBodyJson = objectMapper.writeValueAsString(rootNode);
            return executeGeminiRequest(requestBodyJson);
        } catch (Exception e) {
            log.error("Error generating content from Gemini API", e);
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }

    public String generateContent(String prompt, String mimeType, String base64Data) {
        log.info("Sending content generation request with attachment to Gemini...");
        try {
            ObjectNode rootNode = objectMapper.createObjectNode();
            
            // "contents": [{"parts": [{"text": prompt}, {"inlineData": {"mimeType": mimeType, "data": base64Data}}]}]
            ObjectNode partTextNode = objectMapper.createObjectNode().put("text", prompt);
            ObjectNode partInlineNode = objectMapper.createObjectNode();
            ObjectNode inlineDataNode = objectMapper.createObjectNode()
                    .put("mimeType", mimeType)
                    .put("data", base64Data);
            partInlineNode.set("inlineData", inlineDataNode);
            
            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.putArray("parts").add(partTextNode).add(partInlineNode);
            rootNode.putArray("contents").add(contentNode);

            // "generationConfig": {"responseMimeType": "application/json"}
            ObjectNode genConfig = objectMapper.createObjectNode();
            genConfig.put("responseMimeType", "application/json");
            rootNode.set("generationConfig", genConfig);

            String requestBodyJson = objectMapper.writeValueAsString(rootNode);
            return executeGeminiRequest(requestBodyJson);
        } catch (Exception e) {
            log.error("Error generating multimodal content from Gemini API", e);
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }

    private String executeGeminiRequest(String requestBodyJson) {
        try {
            log.debug("Gemini request body: {}", requestBodyJson);

            HttpUrl url = HttpUrl.parse(GEMINI_API_URL).newBuilder()
                    .addQueryParameter("key", apiKey)
                    .build();

            RequestBody body = RequestBody.create(
                    requestBodyJson, 
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("Gemini API call failed: HTTP {} - {}", response.code(), errorBody);
                    throw new RuntimeException("Gemini API error: HTTP " + response.code() + " " + errorBody);
                }

                String responseBodyStr = response.body().string();
                log.debug("Gemini response body: {}", responseBodyStr);
                
                JsonNode responseJson = objectMapper.readTree(responseBodyStr);
                JsonNode candidates = responseJson.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    JsonNode parts = candidates.get(0).path("content").path("parts");
                    if (parts.isArray() && parts.size() > 0) {
                        String text = parts.get(0).path("text").asText();
                        return sanitizeJson(text);
                    }
                }
                throw new RuntimeException("Invalid response format from Gemini API");
            }
        } catch (IOException e) {
            log.error("Network error calling Gemini API", e);
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }

    private String sanitizeJson(String rawText) {
        if (rawText == null) return "{}";
        String trimmed = rawText.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    public String testConnection() {
        try {
            String response = generateContent("Say 'ZeroHour connection OK' and nothing else.");
            log.info("[GeminiService] Connection test: {}", response);
            return response;
        } catch (Exception e) {
            log.error("[GeminiService] Connection FAILED: {}", e.getMessage(), e);
            return null;
        }
    }
}
