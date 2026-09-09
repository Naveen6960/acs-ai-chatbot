package com.example.azureopenaiapi.controller;
import com.example.azureopenaiapi.agent.OrchestratorAgent;
import com.example.azureopenaiapi.service.ChatLogService;
import com.example.azureopenaiapi.entity.ChatLogEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:4200"}, allowCredentials = "true")
public class ChatController {
    private final OrchestratorAgent orchestratorAgent;
    
    @Autowired
    private ChatLogService chatLogService;
    
    public ChatController(OrchestratorAgent orchestratorAgent) {
        this.orchestratorAgent = orchestratorAgent;
    }

    public static class ChatRequest {
        public String prompt;
        public String agent;
        public String employeeId;
    }

    public static class ChatReply {
        public String response;
        public ChatReply(String response) { this.response = response; }
    }

    @PostMapping(
        path = "/chat",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ChatReply> chat(@RequestBody ChatRequest req) {
        String result = orchestratorAgent.process(req.prompt, req.employeeId);
        return ResponseEntity.ok(new ChatReply(result));
    }

    @GetMapping(path = "/getallchatlogentities", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ChatLogEntity> getChatLogRecords(@RequestParam(required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank()) {
            return chatLogService.getChatLogsByEmployee(employeeId);
        }
        return chatLogService.getAllChatLogEntity();
    }

    @GetMapping(path = "/chatlogs/{employeeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ChatLogEntity> getChatsByEmployee(@PathVariable String employeeId) {
        return chatLogService.getChatLogsByEmployee(employeeId);
    }

}