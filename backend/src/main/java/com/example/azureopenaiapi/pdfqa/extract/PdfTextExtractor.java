package com.example.azureopenaiapi.pdfqa.extract;

import com.example.azureopenaiapi.pdfqa.dto.PageContent;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "pdfqa.enabled", havingValue = "true")
public class PdfTextExtractor {

    // Limit how many pages we process per document to avoid OOM in prototypes
    @Value("${pdfqa.extract.maxPagesPerDoc:20}")
    private int maxPagesPerDoc;

    /**
     * Extracts text page-by-page from a PDF input stream.
     * Uses PDFBox temp-file mode to reduce heap usage.
     */
    public List<PageContent> extractPages(InputStream pdfIn) {
        try (PDDocument doc = PDDocument.load(pdfIn, MemoryUsageSetting.setupTempFileOnly())) {
            List<PageContent> pages = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();

            int total = Math.min(doc.getNumberOfPages(), Math.max(1, maxPagesPerDoc));
            for (int i = 1; i <= total; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc);
                if (text == null) text = "";
                pages.add(new PageContent(i, text.trim()));
            }
            return pages;

        } catch (IOException e) {
            throw new UncheckedIOException("PDF extraction failed", e);
        }
    }
}
