package com.example.azureopenaiapi.pdfqa.source;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "pdfqa.enabled", havingValue = "true")
public class LocalPdfSource implements PdfSource {

    private final String baseDir;

    public LocalPdfSource(@Value("${pdfqa.localDir}") String baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public List<String> listPdfPaths() {
        try (Stream<Path> s = Files.walk(Paths.get(baseDir))) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".pdf"))
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list PDFs in " + baseDir, e);
        }
    }

    @Override
    public InputStream open(String docPath) throws IOException {
        return Files.newInputStream(Paths.get(docPath));
    }
}
