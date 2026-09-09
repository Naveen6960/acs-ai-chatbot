package com.example.azureopenaiapi.pdfqa.summarize;

import com.example.azureopenaiapi.pdfqa.dto.ChatResponse;
import com.example.azureopenaiapi.pdfqa.dto.DocumentReference;
import com.example.azureopenaiapi.pdfqa.dto.DocumentChunk;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PdfSummarizer {

    private static final String REFUSAL =
            "I am unable to answer that question. Please ask agency-related questions.";

    public ChatResponse answer(String question, List<DocumentChunk> chunks, boolean includeSummary) {
        if (chunks == null || chunks.isEmpty()) {
            return new ChatResponse(REFUSAL, List.of(), null, 0);
        }

        // Take top-k chunks by relevance
        List<DocumentChunk> top = topChunks(chunks, 3);

        // Build an extractive answer (pick the most relevant sentences)
        String extractive = buildExtractiveAnswer(question, top);
        if (extractive == null || extractive.isBlank()) {
            extractive = REFUSAL;
        }

        // Citations derived from our top chunks (non-hallucinated)
        StringBuilder cites = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (DocumentChunk c : top) {
            String key = baseName(c.getDocPath()) + "|" + c.getPageNumber();
            if (seen.add(key)) {
                cites.append(String.format("- (%s, p. %d)%n",
                        baseName(c.getDocPath()), c.getPageNumber()));
            }
        }

        String finalAnswer = "**Answer**\n" + extractive.trim()
                + "\n\n**Citations**\n" + cites.toString().trim();

        // References for API consumers
        List<DocumentReference> refs = top.stream().map(c -> {
            DocumentReference r = new DocumentReference();
            r.setFileName(baseName(c.getDocPath()));
            r.setPageNumber(c.getPageNumber());
            r.setRelevanceScore(c.getRelevanceScore());
            r.setContentSnippet(snippet(normalizeText(c.getContent()), 200));
            r.setDocPath(c.getDocPath());
            return r;
        }).collect(Collectors.toList());

        return new ChatResponse(finalAnswer, refs, null, chunks.size());
    }

    // -------- extractive helpers --------

    private List<DocumentChunk> topChunks(List<DocumentChunk> hits, int k) {
        if (hits == null) return List.of();
        return hits.stream()
                .sorted(Comparator.comparingDouble(DocumentChunk::getRelevanceScore).reversed())
                .limit(Math.max(1, k))
                .collect(Collectors.toList());
    }

    private String buildExtractiveAnswer(String question, List<DocumentChunk> chunks) {
        Set<String> qTokens = toKeywords(question);
        List<Candidate> cands = new ArrayList<>();

        for (DocumentChunk c : chunks) {
            String text = normalizeText(c.getContent());
            if (text == null || text.isBlank()) continue;
            for (String st : splitSentences(text)) {
                double sc = keywordScore(qTokens, st);
                // stricter threshold to reduce off-topic lines 0.30
                if (sc >= 0.001) {
                    cands.add(new Candidate(st, c.getDocPath(), c.getPageNumber(), sc));
                }
            }
        }

        if (cands.isEmpty()) return null;

        // Sort by score desc, de-dupe, and keep a few
        cands.sort(Comparator.comparingDouble((Candidate x) -> x.score).reversed());
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        for (Candidate c : cands) {
            String line = c.text.trim();
            if (line.length() < 3) continue;
            if (seen.add(line)) {
                lines.add("- " + line);
            }
            if (lines.size() >= 4) break;
        }
        if (lines.isEmpty()) return null;
        return String.join("\n", lines);
    }

    private static class Candidate {
        final String text;
        final String path;
        final int page;
        final double score;
        Candidate(String text, String path, int page, double score) {
            this.text = text; this.path = path; this.page = page; this.score = score;
        }
    }

    // -------- text utils --------

    private static final Set<String> STOPWORDS = Set.of(
            "a","an","the","and","or","but","if","then","else","when","where","who","what","why","how",
            "is","am","are","be","been","being","to","of","for","in","on","at","by","with","from","as",
            "it","its","this","that","these","those","you","your","yours","we","our","ours",
            "do","does","did","done","can","could","should","would","may","might","will","shall"
    );

    private Set<String> toKeywords(String text) {
        Set<String> s = new HashSet<>();
        if (text == null) return s;
        String norm = text.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[^a-z0-9']+", " ")
                .trim();
        if (norm.isBlank()) return s;
        for (String raw : norm.split("\\s+")) {
            if (raw.length() < 3) continue;
            if (STOPWORDS.contains(raw)) continue;
            String t = stem(raw);
            s.add(t);
        }
        return s;
    }

    private String stem(String t) {
        if (t.startsWith("observ")) return "observ"; // observe/observed/observes/observing
        if (t.equals("holidays")) return "holiday";
        return t;
    }

    private double keywordScore(Set<String> q, String sentence) {
        if (sentence == null || sentence.isBlank() || q.isEmpty()) return 0.0;
        Set<String> s = toKeywords(sentence);
        if (s.isEmpty()) return 0.0;
        int overlap = 0;
        for (String k : q) if (s.contains(k)) overlap++;
        return (double) overlap / (double) Math.max(1, q.size());
    }

    private List<String> splitSentences(String text) {
        String norm = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] parts = norm.split("(?<=[\\.\\!\\?])\\s+|\\n+");
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isBlank()) out.add(t);
        }
        return out;
    }

    private String normalizeText(String s) {
        if (s == null) return null;
        String t = Normalizer.normalize(s, Normalizer.Form.NFKC);
        byte[] bytes = t.getBytes(StandardCharsets.UTF_8);
        t = new String(bytes, StandardCharsets.UTF_8);
        t = t.replace('\u2013', '-').replace('\u2014', '-')
             .replace('\u2018', '\'').replace('\u2019', '\'')
             .replace('\u201C', '"').replace('\u201D', '"');
        t = t.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n{2,}", "\n").trim();
        return t;
    }

    private String snippet(String text, int maxLen) {
        if (text == null) return "";
        String t = text.strip();
        return t.length() <= maxLen ? t : t.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private String baseName(String path) {
        if (path == null) return "";
        int s1 = path.lastIndexOf('\\');
        int s2 = path.lastIndexOf('/');
        int i = Math.max(s1, s2);
        return i >= 0 ? path.substring(i + 1) : path;
    }
}
