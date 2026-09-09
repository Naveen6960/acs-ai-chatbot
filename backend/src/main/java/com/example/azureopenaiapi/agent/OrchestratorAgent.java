package com.example.azureopenaiapi.agent;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class OrchestratorAgent {
    private final ChatAgent chatAgent;

    @Autowired
    public OrchestratorAgent(ChatAgent chatAgent) {
        this.chatAgent = chatAgent;
    }

    public String process(String userInput, String employeeId) {
        return chatAgent.process(userInput, employeeId);
    }
}