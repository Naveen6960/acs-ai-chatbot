package com.example.azureopenaiapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static List<String> tryExtractFollowUps(String raw) {
        try {
            JsonNode n = MAPPER.readTree(raw);
            List<String> out = new ArrayList<>();
            if (n.has("followUps") && n.get("followUps").isArray()) {
                for (JsonNode x : n.get("followUps")) {
                    if (x.isTextual()) out.add(x.asText());
                }
            }
            return out.isEmpty() ? defaultFallback() : out;
        } catch (Exception e) {
            return defaultFallback();
        }
    }

    private static List<String> defaultFallback() {
        return List.of("Show me an example", "Where do I find the form", "Who should I contact");
    }
}
