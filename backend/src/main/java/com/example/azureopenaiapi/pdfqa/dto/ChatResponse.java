package com.example.azureopenaiapi.pdfqa.dto;

import java.util.List;

public class ChatResponse {
    private String answer;
    private List<DocumentReference> references;
    private String summary;
    private int totalDocumentsSearched;

    public ChatResponse() { }

    public ChatResponse(String answer, List<DocumentReference> references, String summary, int totalDocumentsSearched) {
        this.answer = answer;
        this.references = references;
        this.summary = summary;
        this.totalDocumentsSearched = totalDocumentsSearched;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<DocumentReference> getReferences() { return references; }
    public void setReferences(List<DocumentReference> references) { this.references = references; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public int getTotalDocumentsSearched() { return totalDocumentsSearched; }
    public void setTotalDocumentsSearched(int totalDocumentsSearched) { this.totalDocumentsSearched = totalDocumentsSearched; }
}
