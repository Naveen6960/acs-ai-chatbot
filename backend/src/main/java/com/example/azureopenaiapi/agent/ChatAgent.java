package com.example.azureopenaiapi.agent;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.azureopenaiapi.service.ChatLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;            // NEW - import for Logger interface
import org.slf4j.LoggerFactory;     // NEW - import for creating Logger instances

@Component
public class ChatAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ChatAgent.class); // NEW - creates logger for this class

    @org.springframework.beans.factory.annotation.Value("${azure.openai.api.key}")
    private String openAiApiKey;

    @org.springframework.beans.factory.annotation.Value("${azure.openai.endpoint}")
    private String openAiEndpoint;

    @org.springframework.beans.factory.annotation.Value("${azure.openai.deployment}")
    private String openAiDeployment;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ChatLogService chatLogService;

    public ChatAgent() {
    }

    @Override
    public String process(String userInput, String employeeId) {
        log.info("ChatAgent received request"); // NEW - log when request is received
        log.info("Employee ID: {}", employeeId); // NEW - log employee ID
        log.info("User question: {}", userInput); // NEW - log the question
        try {
            String url = String.format("%s/openai/deployments/gpt-4o/chat/completions?api-version=2025-01-01-preview", openAiEndpoint, openAiDeployment);
            log.info("Calling Azure OpenAI URL: {}", url); // NEW - log the URL being called

            java.util.Map<String, Object> userMessage = new java.util.HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", userInput);

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("messages", java.util.List.of(userMessage));

            String json = objectMapper.writeValueAsString(payload);

            String proxyHost = "proxy.acsad.nycnet";
            int proxyPort = 9090;
            ProxySelector proxySelector = ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort));
            HttpClient httpClient = HttpClient.newBuilder()
                    .proxy(proxySelector)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("api-key", openAiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Response received from Azure OpenAI. Status code: {}", response.statusCode()); // NEW - log response status

            // DEBUG: print raw response so we can see exactly what Azure returned
            log.debug("RAW RESPONSE STATUS: {}", response.statusCode()); // NEW - replaced System.out.println
            log.debug("RAW RESPONSE BODY: {}", response.body()); // NEW - replaced System.out.println

            if (response.statusCode() == 200) {
                java.util.Map<?,?> respMap = objectMapper.readValue(response.body(), java.util.Map.class);
                Object choices = respMap.get("choices");
                if (choices instanceof java.util.List && !((java.util.List<?>)choices).isEmpty()) {
                    Object first = ((java.util.List<?>)choices).get(0);
                    if (first instanceof java.util.Map) {
                        Object message = ((java.util.Map<?,?>)first).get("message");
                        if (message instanceof java.util.Map) {
                            Object content = ((java.util.Map<?,?>)message).get("content");
                            if (content != null) {
                                String answer = content.toString();
                                log.info("Answer extracted successfully from GPT-4o response"); // NEW - log when answer is extracted
                                chatLogService.saveChatLog(employeeId != null ? employeeId : "unknown", userInput, answer);
                                log.info("Chat log saved to database for employee: {}", employeeId); // NEW - log when saved to database
                                return answer;
                            }
                        }
                    }
                }
                return "No response from LLM.";
            } else {
                return "Error: " + response.statusCode() + " - " + response.body();
            }
        } catch (Exception e) {
            log.error("Exception occurred while calling Azure OpenAI: {}", e.getMessage()); // NEW - log error
            return "Error calling Azure OpenAI REST API: " + e.getMessage();
        }
    }
}