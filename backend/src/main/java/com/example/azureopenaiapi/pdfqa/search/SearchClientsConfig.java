package com.example.azureopenaiapi.pdfqa.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dedicated clients for the PDF QA prototype so we don't collide with existing beans.
 */
@Configuration
@ConditionalOnProperty(name = "pdfqa.enabled", havingValue = "true")
public class SearchClientsConfig {

    @Bean(name = "pdfqaSearchIndexClient")
    public SearchIndexClient pdfqaSearchIndexClient(
            @Value("${azure.search.endpoint}") String endpoint,
            @Value("${azure.search.api.key}") String apiKey) {
        return new SearchIndexClientBuilder()
                .endpoint(endpoint)                      // e.g., https://<svc>.search.windows.us/.net
                .credential(new AzureKeyCredential(apiKey))
                .buildClient();
    }

    @Bean(name = "pdfqaSearchClient")
    public SearchClient pdfqaSearchClient(
            @Value("${azure.search.endpoint}") String endpoint,
            @Value("${azure.search.api.key}") String apiKey,
            //@Value("${pdfqa.search.indexName}") String indexName) {
            @Value("${azure.search.index}") String indexName) {
        return new SearchClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(apiKey))
                .indexName(indexName)                    // pdfqa-index (from your properties)
                .buildClient();
    }
}
