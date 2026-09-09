package com.example.azureopenaiapi.pdfqa.dto;

public class DocumentChunk {
    private String docPath;
    private int pageNumber;
    private String content;
    private double relevanceScore;

    public DocumentChunk() { }

    public DocumentChunk(String docPath, int pageNumber, String content, double relevanceScore) {
        this.docPath = docPath;
        this.pageNumber = pageNumber;
        this.content = content;
        this.relevanceScore = relevanceScore;
    }

    public String getDocPath() { return docPath; }
    public void setDocPath(String docPath) { this.docPath = docPath; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }
}
