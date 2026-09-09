package com.example.azureopenaiapi.pdfqa.dto;

public class QuestionRequest {

    @jakarta.validation.constraints.NotBlank(message = "Question cannot be empty")
    private String question;

    @jakarta.validation.constraints.Min(value = 1, message = "Max documents must be at least 1")
    @jakarta.validation.constraints.Max(value = 50, message = "Max documents cannot exceed 50")
    private Integer maxDocuments = 10;

    private Boolean includeSummary = true;

    private String employeeId;

    public QuestionRequest() { }

    public QuestionRequest(String question, Integer maxDocuments, Boolean includeSummary) {
        this.question = question;
        this.maxDocuments = maxDocuments;
        this.includeSummary = includeSummary;
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public Integer getMaxDocuments() { return maxDocuments; }
    public void setMaxDocuments(Integer maxDocuments) { this.maxDocuments = maxDocuments; }

    public Boolean getIncludeSummary() { return includeSummary; }
    public void setIncludeSummary(Boolean includeSummary) { this.includeSummary = includeSummary; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
}