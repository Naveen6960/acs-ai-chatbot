package com.example.azureopenaiapi.agent;
import org.springframework.stereotype.Component;
import com.azure.search.documents.SearchClient;

public class SearchRAGAgent implements Agent {
    @org.springframework.beans.factory.annotation.Value("${azure.search.api.key}")
    private String searchApiKey;
    @org.springframework.beans.factory.annotation.Value("${azure.search.endpoint}")
    private String searchEndpoint;
    @org.springframework.beans.factory.annotation.Value("${azure.search.index}")
    private String searchIndex;
    private SearchClient searchClient;

    public SearchRAGAgent() {
        this.searchClient = new com.azure.search.documents.SearchClientBuilder()
            .endpoint(searchEndpoint)
            .credential(new com.azure.core.credential.AzureKeyCredential(searchApiKey))
            .indexName(searchIndex)
            .buildClient();
    }

    @Override
    public String process(String userInput, String employeeId) {
        try {
            com.azure.search.documents.models.SearchOptions options = new com.azure.search.documents.models.SearchOptions();
            Iterable<com.azure.search.documents.models.SearchResult> results = searchClient.search(userInput, options, null);
            StringBuilder docs = new StringBuilder();
            for (com.azure.search.documents.models.SearchResult result : results) {
                java.util.Map<String, Object> doc = result.getDocument(java.util.Map.class);
                docs.append(doc.toString()).append("\n");
            }
            String ragPrompt = "User question: " + userInput + "\nRelevant documents:\n" + docs.toString() + "\nAnswer the question using the provided documents.";
            String answer = callLLM(ragPrompt);
            return answer;
        } catch (Exception e) {
            return "Error in SearchRAGAgent: " + e.getMessage();
        }
    }

    private String callLLM(String prompt) {
        return prompt;
    }
}