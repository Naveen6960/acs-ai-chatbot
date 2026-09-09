package com.example.azureopenaiapi.pdfqa.dto;

public class PageContent {
    private int pageNumber;
    private String text;

    public PageContent() { }

    public PageContent(int pageNumber, String text) {
        this.pageNumber = pageNumber;
        this.text = text;
    }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
