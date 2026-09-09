package com.example.azureopenaiapi.pdfqa.ingest;

import com.azure.search.documents.SearchClient;
import com.example.azureopenaiapi.pdfqa.chunk.Chunker;
import com.example.azureopenaiapi.pdfqa.dto.DocumentChunk;
import com.example.azureopenaiapi.pdfqa.dto.PageContent;
import com.example.azureopenaiapi.pdfqa.embedding.EmbeddingService;
import com.example.azureopenaiapi.pdfqa.extract.PdfTextExtractor;
import com.example.azureopenaiapi.pdfqa.search.PdfSearchIndex;
import com.example.azureopenaiapi.pdfqa.source.PdfSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

@Service
@ConditionalOnProperty(name = "pdfqa.enabled", havingValue = "true")
public class PdfIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PdfIngestionService.class);

    private final PdfSource source;
    private final PdfTextExtractor extractor;
    private final Chunker chunker;
    private final EmbeddingService embeddingService;
    private final PdfSearchIndex pdfSearchIndex;

    public PdfIngestionService(
            PdfSource source,
            PdfTextExtractor extractor,
            Chunker chunker,
            EmbeddingService embeddingService,
            PdfSearchIndex pdfSearchIndex) {
        this.source = source;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.pdfSearchIndex = pdfSearchIndex;
    }

    /** Ingests every PDF under pdfqa.localDir into the Azure AI Search index. */
    public int ingestAll() {
        SearchClient search = pdfSearchIndex.client();
        List<String> paths = source.listPdfPaths();
        int totalChunks = 0;

        for (String docPath : paths) {
            try (InputStream in = source.open(docPath)) {
                List<PageContent> pages = extractor.extractPages(in);

                // Build docs as plain Map<String,Object>
                List<Map<String, Object>> docs = new ArrayList<>();
                for (PageContent page : pages) {
                    for (DocumentChunk c : chunker.chunk(docPath, page)) {
                        float[] vec = embeddingService.embed(c.getContent());

                        Map<String, Object> sd = new HashMap<>();
                        sd.put("id", stableId(docPath, c.getPageNumber(), c.getContent()));
                        sd.put("docPath", docPath);
                        sd.put("pageNumber", c.getPageNumber());
                        sd.put("text", c.getContent());
                        sd.put("vector", vec); // vector field = collection(Edm.Single)

                        docs.add(sd);
                    }
                }

                if (!docs.isEmpty()) {
                    search.mergeOrUploadDocuments(docs); // simple upsert
                    totalChunks += docs.size();
                }
                log.info("Ingested {} chunks from {}", docs.size(), docPath);

            } catch (UncheckedIOException ioe) {
                log.warn("Skipping unreadable PDF: {}", docPath, ioe);
            } catch (Exception e) {
                log.warn("Ingestion failed for {}", docPath, e);
            }
        }
        return totalChunks;
    }

    private String stableId(String docPath, int page, String content) {
        String base = docPath + "|" + page + "|" + Integer.toHexString(content.hashCode());
        return UUID.nameUUIDFromBytes(base.getBytes()).toString();
    }
}
