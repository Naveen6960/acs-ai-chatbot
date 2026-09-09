package com.example.azureopenaiapi.pdfqa.chunk;

import com.example.azureopenaiapi.pdfqa.dto.DocumentChunk;
import com.example.azureopenaiapi.pdfqa.dto.PageContent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "pdfqa.enabled", havingValue = "true")
public class Chunker {

    // Target chunk size (in characters), overlap, and hard safety caps.
    private static final int CHARS_TARGET = 1200;     // ~300–500 tokens
    private static final int CHARS_OVERLAP = 160;     // ~10–15% overlap
    private static final int MAX_CHARS_PER_PAGE = 20_000;   // never consider >20k chars from a single page
    private static final int MAX_CHUNKS_PER_PAGE = 40;      // never emit >40 chunks per page

    /**
     * Split a page into overlapping chunks with strict caps to avoid high memory use.
     */
    public List<DocumentChunk> chunk(String docPath, PageContent page) {
        String t = page.getText();
        if (t == null || (t = t.trim()).isEmpty()) {
            return List.of();
        }

        // Hard cap: ignore text beyond MAX_CHARS_PER_PAGE to prevent runaway chunking.
        if (t.length() > MAX_CHARS_PER_PAGE) {
            t = t.substring(0, MAX_CHARS_PER_PAGE);
        }

        List<DocumentChunk> out = new ArrayList<>(Math.min(8, MAX_CHUNKS_PER_PAGE));
        int start = 0;
        int made = 0;

        while (start < t.length() && made < MAX_CHUNKS_PER_PAGE) {
            int end = Math.min(start + CHARS_TARGET, t.length());

            // Prefer ending at a sentence boundary (only within the current window)
            int lastDot = t.lastIndexOf('.', end);
            if (lastDot >= start + 80 && lastDot <= end) {
                end = Math.min(lastDot + 1, t.length());
            }

            // Slice defensively
            if (end <= start) break;
            String slice = t.substring(start, end).trim();
            if (!slice.isEmpty()) {
                out.add(new DocumentChunk(docPath, page.getPageNumber(), slice, 0.0));
                made++;
            }

            if (end >= t.length()) break;
            start = Math.max(start + CHARS_TARGET - CHARS_OVERLAP, end - CHARS_OVERLAP);
        }

        return out;
    }
}
