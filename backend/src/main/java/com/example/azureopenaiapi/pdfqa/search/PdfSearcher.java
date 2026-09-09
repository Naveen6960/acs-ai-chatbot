package com.example.azureopenaiapi.pdfqa.search;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.models.QueryAnswer;
import com.azure.search.documents.models.QueryAnswerType;
import com.azure.search.documents.models.QueryCaption;
import com.azure.search.documents.models.QueryCaptionType;
import com.azure.search.documents.models.QueryType;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchResult;
import com.azure.search.documents.models.SemanticSearchOptions;
import com.example.azureopenaiapi.pdfqa.chunk.Chunker;
import com.example.azureopenaiapi.pdfqa.dto.DocumentChunk;
import com.example.azureopenaiapi.pdfqa.dto.PageContent;
import com.example.azureopenaiapi.pdfqa.extract.PdfTextExtractor;
import com.example.azureopenaiapi.pdfqa.source.PdfSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.azure.search.documents.models.VectorQuery;
import com.azure.search.documents.models.VectorizedQuery;
import com.azure.search.documents.util.SearchPagedIterable;
import com.azure.search.documents.models.VectorSearchOptions;
import com.azure.search.documents.models.VectorizableTextQuery;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;           // NEW - imports Logger interface from SLF4J
import org.slf4j.LoggerFactory;    // NEW - imports LoggerFactory to create Logger instances

@Service
@ConditionalOnProperty(name = "pdfqa.enabled", havingValue = "true")
public class PdfSearcher {

    private static final Logger log = LoggerFactory.getLogger(PdfSearcher.class); // NEW - logger declared

    private final PdfSearchIndex pdfSearchIndex;
    private final PdfSource source;
    private final PdfTextExtractor extractor;
    private final Chunker chunker;

    @Value("${pdfqa.search.topK:8}")
    private int topK;

    // When true we’ll use Azure Search; when false we’ll use the offline local scan.
    //@Value("${pdfqa.search.bootstrap:false}")
    @Value("${pdfqa.search.bootstrap}")
    private boolean useAzureSearch;

    private final OpenAIClient openAIClient;

    // Constructor injection (recommended)




    // Offline safety caps (prevents OOM on big folders)
    private static final int OFFLINE_MAX_DOCS = 5;
    private static final int OFFLINE_MAX_PAGES_PER_DOC = 200;
    private static final int OFFLINE_MAX_CHUNKS = 5000;

    @org.springframework.beans.factory.annotation.Value("${azure.openai.api.key}")
    private String openAiApiKey;
    @org.springframework.beans.factory.annotation.Value("${azure.openai.endpoint}")
    private String openAiEndpoint;
    @org.springframework.beans.factory.annotation.Value("${azure.openai.deployment}")
    private String openAiDeployment;
    @org.springframework.beans.factory.annotation.Value("${azure.search.index.semantic.configuration}")
    private String azureSearchIndexSemanticConfiguration;

 // Constructor injection (recommended)
    public PdfSearcher(PdfSearchIndex pdfSearchIndex,
                       PdfSource source,
                       PdfTextExtractor extractor,
                       Chunker chunker,OpenAIClient openAIClient) {
        this.pdfSearchIndex = pdfSearchIndex;
        this.source = source;
        this.extractor = extractor;
        this.chunker = chunker;
        this.openAIClient = openAIClient;
    }

    /** Retrieve top chunks for a query. Uses Azure Search when enabled; otherwise scans local PDFs. */
    public List<DocumentChunk> search(String query) {
        log.info("search() called with query: {}", query); // NEW - log when search is called
        if (useAzureSearch) {
            return searchWithAzure(query);
        	//return searchWithAzureVector(query);
        } else {
            return searchOfflineLocal(query);
        }
    }



    // ---------- Azure Search path (keyword-only for portability; vector can be added later) ----------
    private List<DocumentChunk> searchWithAzure(String query) {
        log.info("Starting Azure keyword search for query: {}", query); // NEW
        try {
            SearchClient client = pdfSearchIndex.client();

            SearchOptions opts = new SearchOptions()
                    .setTop(topK)
                    .setSelect("docPath", "pageNumber", "text");

            // Some SDK versions require the 3-arg form with a Context
            Iterable<SearchResult> results = client.search(query, opts, null);

            List<DocumentChunk> out = new ArrayList<>();
            for (SearchResult r : results) {
                Map<?, ?> doc = r.getDocument(Map.class);
                String path = (String) doc.get("docPath");
                Object pn = doc.get("pageNumber");
                int page = pn instanceof Number ? ((Number) pn).intValue() : 0;
                String text = (String) doc.get("text");
                double score = 0.0;
                try { score = r.getScore(); } catch (Throwable ignored) {}

                out.add(new DocumentChunk(path, page, text == null ? "" : text, score));
                if (out.size() >= topK) break;
            }
            log.info("Azure keyword search returned {} results", out.size()); // NEW when Azure keyword search starts
            return out;

        } catch (Exception e) {
            // If Search is unreachable or misconfigured, fallback to local
            return searchOfflineLocal(query);
        }
    }



    public String searchWithRag(String query) {
        log.info("searchWithRag called with query: {}", query); // NEW  when searchWithRag is called
        return searchWithAzureVector(query);
   }

 // ---------- Azure Search path ( vector ) ----------
    private String searchWithAzureVector(String query) {
        log.info("Starting Azure Vector Search for query: {}", query); // NEW  when vector search starts
    	 List<DocumentChunk> out = new ArrayList<>();
    	 String answer = "No response from LLM.";
    	try {
            SearchClient searchClient = pdfSearchIndex.client();

         // Create a VectorizableTextQuery — passing the text to vectorize server-side
            VectorizableTextQuery textQuery = new VectorizableTextQuery(query)
                    .setFields(new String[] { "text_vector" })
                    .setKNearestNeighborsCount(5);


         // Wrap it in VectorSearchOptions
            VectorSearchOptions vsOptions = new VectorSearchOptions()
                    .setQueries(List.of((VectorQuery) textQuery));

            SearchOptions options = new SearchOptions()
            		.setSemanticSearchOptions(new SemanticSearchOptions()
                            .setSemanticConfigurationName(azureSearchIndexSemanticConfiguration)
                            .setQueryAnswer(new QueryAnswer(QueryAnswerType.EXTRACTIVE))
                            .setQueryCaption(new QueryCaption(QueryCaptionType.EXTRACTIVE)))
            		.setQueryType(QueryType.SEMANTIC)
                    .setVectorSearchOptions(vsOptions)
                    .setTop(5)
                    .setSelect("chunk_id", "title", "chunk", "docPath");
                    // you could also set semantic config, filters, etc.


            // Run the search — pass the text query and the SearchOptions
            log.info("Sending vector search request to Azure AI Search"); // NEW when request is sent
            SearchPagedIterable results = searchClient.search(query, options, Context.NONE);


            log.info("Integrated vectorization search results:"); // NEW - replaced System.out.println


            List<String> topDocs = new ArrayList<>();

            for (SearchResult result : results) {
                Map<String, Object> doc = result.getDocument(Map.class);

                System.out.println("-score " + result.getScore()+ "- "+ doc.get("title") + " | " + doc.get("chunk") + " | " + doc.get("docPath"));
                String text = (String) doc.get("chunk");
                String path = (String) doc.get("docPath");
                double score = 0.0;
                try { score = result.getScore(); } catch (Throwable ignored) {}
                out.add(new DocumentChunk(path, 0, text == null ? "" : text, score));
                topDocs.add(doc.get("chunk").toString());
                log.info("Azure AI Search returned {} relevant documents", topDocs.size()); // NEW  how many documents found
                if (out.size() >= topK) break;
            }



         // Build contextual prompt
            String context = String.join("\n\n", topDocs);
            //String ragPrompt = "User question: " + userInput + "\nRelevant documents:\n" + docs.toString() + "\nAnswer the question using the provided documents.";
            String systemPrompt = "You are an AI assistant that answers questions using the provided context. Format your answers using Markdown bullet points. Include source document/s name at the bottom."
                    + "If the answer is not in the context, say you don't know.";
            String userPrompt = String.format("Context:\n%s\n\nQuestion:\n%s", context, query);

         // Initialize clients


            //ProxyOptions proxyOptions = new ProxyOptions(ProxyOptions.Type.HTTP,
            //        new InetSocketAddress("proxy.acsad.nycnet", 9090));

            //HttpClient httpClient = new NettyAsyncHttpClientBuilder()
            //        .proxy(proxyOptions)
            //        .build();

            //OpenAIClient openAiClient = new OpenAIClientBuilder()
            //        .endpoint(openAiEndpoint)
            //        .credential(new AzureKeyCredential(openAiApiKey))
            //        .httpClient(httpClient)
            //        .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BASIC))
            //        .buildClient();

         // Call OpenAI Chat model
         // Build the list of messages
            List<ChatRequestMessage> messages = new ArrayList<>();
            messages.add(new ChatRequestSystemMessage(systemPrompt));
            messages.add(new ChatRequestUserMessage(userPrompt));

         // Create the options with messages
            ChatCompletionsOptions chatOptions = new ChatCompletionsOptions(messages)
                .setModel(openAiDeployment)   // set your deployment/model name
                .setMaxTokens(512)         // optional: limit output length
                .setTemperature(0.7);      // optional: control randomness

            // Call the chat completions API
            log.info("Calling Azure OpenAI GPT-4o for answer generation"); // NEW  when GPT-4o is called
            ChatCompletions chatResponse = openAIClient.getChatCompletions(openAiDeployment, chatOptions);

            // Extract the assistant’s message
            answer = chatResponse.getChoices().get(0).getMessage().getContent();

            // Output result
            log.info("Answer generated successfully: {}", answer); // NEW - replaced System.out.println when answer is generated





            return answer;

    	} catch (Exception e) {
            log.error("PdfSearcher OpenAI ERROR: {}", e.getMessage()); // NEW - replaced System.out.println
    	    e.printStackTrace();
    	    return answer;
    	}
    }


    // ---------- Offline fallback: keyword scoring with hard caps (no embeddings) ----------
    private List<DocumentChunk> searchOfflineLocal(String query) {
        log.info("Running offline local search for query: {}", query); // NEW when offline search runs
        Set<String> qTokens = toKeywords(query);

        List<DocumentChunk> candidates = new ArrayList<>();
        int docCount = 0;
        int chunkCount = 0;

        for (String docPath : source.listPdfPaths()) {
            if (docCount >= OFFLINE_MAX_DOCS) break;
            docCount++;

            try (InputStream in = source.open(docPath)) {
                List<PageContent> pages = extractor.extractPages(in);

                int pageSeen = 0;
                for (PageContent p : pages) {
                    if (pageSeen >= OFFLINE_MAX_PAGES_PER_DOC) break;
                    pageSeen++;

                    for (DocumentChunk c : chunker.chunk(docPath, p)) {
                        double score = keywordScore(qTokens, c.getContent());
                        candidates.add(new DocumentChunk(c.getDocPath(), c.getPageNumber(), c.getContent(), score));
                        chunkCount++;
                        if (chunkCount >= OFFLINE_MAX_CHUNKS) break;
                    }
                    if (chunkCount >= OFFLINE_MAX_CHUNKS) break;
                }
            } catch (Exception ignored) {
                // unreadable PDF? skip
            }

            if (chunkCount >= OFFLINE_MAX_CHUNKS) break;
        }

        log.info("Offline search completed. Found {} candidate chunks", candidates.size()); // NEW offline results


        return candidates.stream()
                .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private Set<String> toKeywords(String text) {
        Set<String> s = new HashSet<>();
        if (text == null) return s;
        for (String t : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (!t.isBlank() && t.length() > 2) s.add(t); // drop 1–2 letter noise
        }
        return s;
    }

    private double keywordScore(Set<String> qTokens, String content) {
        if (content == null || qTokens.isEmpty()) return 0.0;
        Set<String> c = toKeywords(content);
        if (c.isEmpty()) return 0.0;
        int overlap = 0;
        for (String t : qTokens) if (c.contains(t)) overlap++;
        return (double) overlap / (double) qTokens.size(); // 0..1
    }
}
