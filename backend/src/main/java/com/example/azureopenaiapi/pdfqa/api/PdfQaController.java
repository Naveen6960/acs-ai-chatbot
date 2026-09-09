package com.example.azureopenaiapi.pdfqa.api;

import com.example.azureopenaiapi.pdfqa.source.PdfSource;
import com.example.azureopenaiapi.service.ChatLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.azureopenaiapi.pdfqa.dto.ChatResponse;
import com.example.azureopenaiapi.pdfqa.dto.DocumentChunk;
import com.example.azureopenaiapi.pdfqa.dto.QuestionRequest;
import com.example.azureopenaiapi.pdfqa.ingest.PdfIngestionService;
import com.example.azureopenaiapi.pdfqa.search.PdfSearcher;
import com.example.azureopenaiapi.pdfqa.summarize.PdfSummarizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(
        origins = {
                "http://localhost:4200",
                "http://localhost:3000",
                "http://localhost:5173"
        },
        allowCredentials = "true"
)

@RestController
@RequestMapping("/api/pdfqa")
@ConditionalOnProperty(name = "pdfqa.enabled", havingValue = "true")
@Validated
public class PdfQaController {

    private static final Logger log = LoggerFactory.getLogger(PdfQaController.class);

    private final PdfIngestionService ingestionService;
    private final PdfSearcher searcher;
    private final PdfSummarizer summarizer;
    private final PdfSource source;

    @Autowired
    private ChatLogService chatLogService;

    @Value("${pdfqa.search.indexName}")
    private String indexName;

    @Value("${pdfqa.localDir}")
    private String pdfLocalDir;

    public PdfQaController(PdfIngestionService ingestionService,
                           PdfSearcher searcher,
                           PdfSummarizer summarizer,
                           PdfSource source) {
        this.ingestionService = ingestionService;
        this.searcher = searcher;
        this.summarizer = summarizer;
        this.source = source;
    }

    @GetMapping("/docs")
    public Map<String, Object> listDocs() {
        log.info("Fetching list of documents");
        var paths = source.listPdfPaths();
        log.info("Total documents found: {}", paths.size());
        var items = new java.util.ArrayList<java.util.Map<String, String>>();
        for (String p : paths) {
            items.add(java.util.Map.of(
                    "fileName", baseName(p),
                    "docPath", p
            ));
        }
        return java.util.Map.of(
                "count", items.size(),
                "documents", items
        );
    }

    private String baseName(String path) {
        if (path == null) return "";
        int s1 = path.lastIndexOf('\\');
        int s2 = path.lastIndexOf('/');
        int i = Math.max(s1, s2);
        return i >= 0 ? path.substring(i + 1) : path;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingestAll() {
        log.info("Starting ingestion of all PDF documents");
        int count = ingestionService.ingestAll();
        log.info("Ingestion complete. Total chunks indexed: {}", count);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("indexedChunks", count);
        resp.put("index", indexName);
        return resp;
    }

    @PostMapping("/ask")
    public ChatResponse ask(@Valid @RequestBody QuestionRequest req) {
        log.info("Received /ask request with question: {}", req.getQuestion());
        var hits = searcher.search(req.getQuestion());

        final double MIN_SCORE = 0.15;
        if (hits == null || hits.isEmpty()) {
            log.warn("No search results found for question: {}", req.getQuestion());
            return new ChatResponse(
                    "I am unable to answer that question. Please ask agency-related questions.",
                    List.of(), null, 0
            );
        }

        double maxScore = hits.stream()
                .mapToDouble(h -> {
                    try { return h.getRelevanceScore(); } catch (Exception e) { return 0.0; }
                })
                .max()
                .orElse(0.0);

        if (maxScore < MIN_SCORE) {
            log.warn("Max relevance score {} is below minimum threshold for question: {}", maxScore, req.getQuestion());
            return new ChatResponse(
                    "I am unable to answer that question. Please ask agency-related questions.",
                    List.of(), null, 0
            );
        }

        log.info("Search successful. Max score: {}. Generating answer.", maxScore);
        boolean includeSummary = Boolean.TRUE.equals(req.getIncludeSummary());
        return summarizer.answer(req.getQuestion(), hits, includeSummary);
    }

    @PostMapping("/ask-structured_original")
    public Map<String, Object> askStructuredoriginal(@Valid @RequestBody QuestionRequest req) {
        log.info("Received /ask-structured_original request with question: {}", req.getQuestion());
        var hits = searcher.search(req.getQuestion());

        final double MIN_SCORE = 0.001;
        if (hits == null || hits.isEmpty()) {
            log.warn("No search results found for question: {}", req.getQuestion());
            return Map.of(
                    "answer", "I am unable to answer that question. Please ask agency-related questions.",
                    "bullets", List.of(),
                    "citations", List.of(),
                    "references", List.of(),
                    "summary", null,
                    "totalDocumentsSearched", 0
            );
        }

        double maxScore = hits.stream()
                .mapToDouble(h -> {
                    try { return h.getRelevanceScore(); } catch (Exception e) { return 0.0; }
                })
                .max()
                .orElse(0.0);

        if (maxScore < MIN_SCORE) {
            log.warn("Max relevance score {} is below minimum threshold for question: {}", maxScore, req.getQuestion());
            return Map.of(
                    "answer", "I am unable to answer that question. Please ask agency-related questions.",
                    "bullets", List.of(),
                    "citations", List.of(),
                    "references", List.of(),
                    "summary", null,
                    "totalDocumentsSearched", 0
            );
        }

        log.info("Search successful. Max score: {}. Generating answer.", maxScore);
        boolean includeSummary = Boolean.TRUE.equals(req.getIncludeSummary());
        var resp = summarizer.answer(req.getQuestion(), hits, includeSummary);

        List<String> bullets = parseBullets(resp.getAnswer());
        List<Map<String, Object>> citations = parseCitations(resp.getAnswer());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", resp.getAnswer());
        out.put("bullets", bullets == null ? List.of() : bullets);
        out.put("citations", citations == null ? List.of() : citations);
        out.put("references", resp.getReferences());
        out.put("summary", resp.getSummary());
        out.put("totalDocumentsSearched", resp.getTotalDocumentsSearched());
        return out;
    }

    @PostMapping("/ask-structured")
    public Map<String, Object> askStructured(@Valid @RequestBody QuestionRequest req) {
        log.info("Received /ask-structured request");
        log.info("Question: {}", req.getQuestion());
        log.info("Employee ID: {}", req.getEmployeeId());

        String answer = searcher.searchWithRag(req.getQuestion());
        log.info("Answer generated successfully from Azure AI Search and GPT-4o");

        // Run keyword search to get citations with real page numbers and doc paths
        var hits = searcher.search(req.getQuestion());
        List<Map<String, Object>> citations = hits.stream()
            .filter(h -> h.getDocPath() != null)
            .limit(3)
            .map(h -> {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("fileName", safeBaseName(h.getDocPath()));
                c.put("pageNumber", h.getPageNumber());
                c.put("docPath", h.getDocPath());
                return c;
            })
            .collect(Collectors.toList());

        String employeeId = req.getEmployeeId() != null ? req.getEmployeeId() : "unknown";
        chatLogService.saveChatLog(employeeId, req.getQuestion(), answer);
        log.info("Chat log saved to database for employee: {}", employeeId);

        List<String> bullets = parseBullets(answer);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", answer);
        out.put("bullets", bullets == null ? List.of() : bullets);
        out.put("citations", citations);
        out.put("references", List.of());
        out.put("summary", answer);
        out.put("totalDocumentsSearched", hits.size());

        log.info("Response prepared and returning to frontend");
        return out;
    }

    @GetMapping("/page-image")
    public ResponseEntity<byte[]> getPageImage(
            @RequestParam String docPath,
            @RequestParam(defaultValue = "1") int page) {
        try {
            File pdfFile = new File(docPath);
            if (!pdfFile.exists() || !docPath.toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.notFound().build();
            }
            try (PDDocument document = PDDocument.load(pdfFile)) {
                PDFRenderer renderer = new PDFRenderer(document);
                int pageIndex = Math.max(0, page - 1);
                if (pageIndex >= document.getNumberOfPages()) {
                    pageIndex = document.getNumberOfPages() - 1;
                }
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", baos);
                log.info("Rendered page {} of {}", page, pdfFile.getName());
                return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(baos.toByteArray());
            }
        } catch (Exception e) {
            log.error("Error rendering PDF page: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private String safeBaseName(String path) {
        if (path == null) return "";
        int s1 = path.lastIndexOf('\\');
        int s2 = path.lastIndexOf('/');
        int i = Math.max(s1, s2);
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private List<String> parseBullets(String answerMd) {
        if (answerMd == null) return List.of();
        List<String> out = new ArrayList<>();
        boolean inAnswer = false;
        for (String raw : answerMd.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.equalsIgnoreCase("**Answer**")) { inAnswer = true; continue; }
            if (line.equalsIgnoreCase("**Citations**")) break;
            if (inAnswer && line.startsWith("- ")) {
                out.add(line.substring(2).trim());
            }
        }
        return out;
    }

    private List<Map<String,Object>> parseCitations(String answerMd) {
        if (answerMd == null) return List.of();
        List<Map<String,Object>> out = new ArrayList<>();
        boolean inCites = false;
        for (String raw : answerMd.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.equalsIgnoreCase("**Citations**")) { inCites = true; continue; }
            if (!inCites) continue;
            if (!line.startsWith("- ")) continue;
            int l = line.indexOf('('), r = line.indexOf(')');
            if (l >= 0 && r > l) {
                String inside = line.substring(l + 1, r);
                String[] parts = inside.split(",\\s*p\\.\\s*", 2);
                if (parts.length == 2) {
                    String file = parts[0].trim();
                    try {
                        int page = Integer.parseInt(parts[1].trim());
                        out.add(Map.of("fileName", file, "pageNumber", page));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return out;
    }

    private Map<String, Object> refusal() {
        return Map.of(
                "answer", "I am unable to answer that question. Please ask agency-related questions.",
                "bullets", List.of(),
                "citations", List.of(),
                "references", List.of(),
                "summary", null,
                "totalDocumentsSearched", 0
        );
    }

    private static final Set<String> STOPWORDS = Set.of(
            "a","an","the","and","or","but","if","then","else","when","where","who","what","why","how",
            "is","am","are","be","been","being","to","of","for","in","on","at","by","with","from","as",
            "it","its","this","that","these","those","you","your","yours","we","our","ours",
            "do","does","did","done","can","could","should","would","may","might","will","shall"
    );

    private int keywordOverlap(String q, String text) {
        Set<String> qk = keywords(q);
        if (qk.isEmpty()) return 0;
        Set<String> tk = keywords(text);
        int count = 0;
        for (String k : qk) if (tk.contains(k)) count++;
        return count;
    }

    private Set<String> keywords(String s) {
        if (s == null) return Set.of();
        String[] raw = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().split("\\s+");
        Set<String> out = new HashSet<>();
        for (String t : raw) {
            if (t.length() < 3) continue;
            if (STOPWORDS.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    @GetMapping("/debug-search")
    public Map<String, Object> debugSearch(@RequestParam("q") String q) {
        log.info("Debug search triggered with query: {}", q);
        var hits = searcher.search(q);
        log.info("Debug search returned {} hits", hits.size());
        var rows = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (int i = 0; i < hits.size(); i++) {
            var c = hits.get(i);
            String snip = c.getContent();
            if (snip == null) snip = "";
            if (snip.length() > 220) snip = snip.substring(0, 220) + "...";
            rows.add(java.util.Map.of(
                    "rank", i + 1,
                    "score", c.getRelevanceScore(),
                    "docPath", c.getDocPath(),
                    "page", c.getPageNumber(),
                    "snippet", snip
            ));
        }
        return java.util.Map.of(
                "query", q,
                "topK", hits.size(),
                "hits", rows
        );
    }
}