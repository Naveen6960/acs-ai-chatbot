package com.example.azureopenaiapi.pdfqa.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@ConditionalOnProperty(name = "pdfqa.enabled", havingValue = "true")
public class PdfQaExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PdfQaExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleAny(Exception ex, HttpServletRequest req) {
        log.error("[pdfqa] Uncaught exception on {} {}", req.getMethod(), req.getRequestURI(), ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 500);
        body.put("error", ex.getClass().getName());
        body.put("message", ex.getMessage());
        body.put("path", req.getRequestURI());
        if (ex.getCause() != null) {
            body.put("cause", ex.getCause().getClass().getName() + ": " + ex.getCause().getMessage());
        }
        StackTraceElement[] st = ex.getStackTrace();
        int n = Math.min(3, st.length);
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < n; i++) top.append(st[i].toString()).append("\n");
        body.put("stackTop", top.toString().trim());
        return body;
    }
}
