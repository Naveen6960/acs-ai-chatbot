package com.example.azureopenaiapi.pdfqa.search;

import com.azure.search.documents.SearchClient;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.models.HnswAlgorithmConfiguration;
import com.azure.search.documents.indexes.models.SearchField;
import com.azure.search.documents.indexes.models.SearchFieldDataType;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.indexes.models.VectorSearch;
import com.azure.search.documents.indexes.models.VectorSearchProfile;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "pdfqa.enabled", havingValue = "true")
public class PdfSearchIndex {

    private final SearchIndexClient indexClient;
    private final SearchClient searchClient;
    private final String indexName;

   @Value("${pdfqa.search.bootstrap:true}")    
    private boolean bootstrapOnStartup;

    public PdfSearchIndex(
            @Qualifier("pdfqaSearchIndexClient") SearchIndexClient indexClient,
            @Qualifier("pdfqaSearchClient") SearchClient searchClient,
            //@Value("${pdfqa.search.indexName}") String indexName) {
            @Value("${azure.search.index}") String indexName) {
        this.indexClient = indexClient;
        this.searchClient = searchClient;
        this.indexName = indexName;
    }

    @PostConstruct
    public void ensureIndex() {
        // Offline-friendly: skip any network call unless explicitly enabled
        if (!bootstrapOnStartup) {
            System.out.println("[pdfqa] Skipping Azure Search bootstrap (pdfqa.search.bootstrap=false).");
            return;
        }

        // If index already exists, nothing to do
        try {
            indexClient.getIndex(indexName);
            System.out.println("[pdfqa] Index '" + indexName + "' already exists.");
            return;
        } catch (Exception ignored) {
            // proceed to create
        }

        try {
            // Fields
            SearchField id = new SearchField("id", SearchFieldDataType.STRING)
                    .setKey(true)
                    .setFilterable(true);

            SearchField docPath = new SearchField("docPath", SearchFieldDataType.STRING)
                    .setSearchable(true)
                    .setFilterable(true);

            SearchField pageNumber = new SearchField("pageNumber", SearchFieldDataType.INT32)
                    .setFilterable(true);

            SearchField text = new SearchField("text", SearchFieldDataType.STRING)
                    .setSearchable(true);

            // Vector field: 3072 dims for text-embedding-3-large (use 1536 if you switch to -small)
            SearchField vector = new SearchField("vector",
                    SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
                    .setVectorSearchDimensions(3072)
                    .setVectorSearchProfileName("pdfqa-vector-profile");

            SearchIndex index = new SearchIndex(indexName)
                    .setFields(List.of(id, docPath, pageNumber, text, vector))
                    .setVectorSearch(new VectorSearch()
                            .setAlgorithms(List.of(new HnswAlgorithmConfiguration("hnsw")))
                            .setProfiles(List.of(new VectorSearchProfile("pdfqa-vector-profile", "hnsw"))));

            indexClient.createIndex(index);
            System.out.println("[pdfqa] Created Azure Search index '" + indexName + "'.");
        } catch (Exception e) {
            // Stay up even if Azure isn't reachable (offline prototype)
            System.out.println("[pdfqa] Azure Search not reachable or create failed; running in offline mode. Reason: " + e.getMessage());
        }
    }

    public SearchClient client() {
        return searchClient;
    }
}
