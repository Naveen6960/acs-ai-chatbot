package com.example.azureopenaiapi.pdfqa.dto;

/** Frontend-friendly citation for a retrieved passage. */
public class DocumentReference {
    /** Just the filename, e.g., "BrightWave Solutions.pdf" */
    private String fileName;

    /** 1-based page number */
    private int pageNumber;

    /** Retrieval score (0..1 offline, or search score with Azure Search) */
    private double relevanceScore;

    /** Short preview snippet from the page/chunk */
    private String contentSnippet;

    /** Full path (optional, for debugging/logging) */
    private String docPath;

    public DocumentReference() { }

    public DocumentReference(String fileName, int pageNumber, double relevanceScore,
                             String contentSnippet, String docPath) {
        this.fileName = fileName;
        this.pageNumber = pageNumber;
        this.relevanceScore = relevanceScore;
        this.contentSnippet = contentSnippet;
        this.docPath = docPath;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }

    public String getContentSnippet() { return contentSnippet; }
    public void setContentSnippet(String contentSnippet) { this.contentSnippet = contentSnippet; }

    public String getDocPath() { return docPath; }
    public void setDocPath(String docPath) { this.docPath = docPath; }
}
