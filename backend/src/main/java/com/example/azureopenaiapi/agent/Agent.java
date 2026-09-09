package com.example.azureopenaiapi.agent;

public interface Agent {
    String process(String userInput, String employeeId);
}