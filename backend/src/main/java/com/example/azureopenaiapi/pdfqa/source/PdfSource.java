package com.example.azureopenaiapi.pdfqa.source;

import java.io.InputStream;
import java.io.IOException;
import java.util.List;

public interface PdfSource {
    List<String> listPdfPaths();
    InputStream open(String docPath) throws IOException;
}
