package com.example.azureopenaiapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:4200"}, allowCredentials = "true")
public class FeedbackController {

  private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);
  private static final Logger feedbackLog = LoggerFactory.getLogger("feedback");
  private final ObjectMapper mapper = new ObjectMapper();

  public static class FeedbackRequest {
    private String messageId;
    private String verdict;       
    private String agent;          
    private String userText;
    private String assistantText;
    private Map<String, Object> meta;

    public FeedbackRequest() {}

    public String getMessageId() { return messageId; }
    public String getVerdict() { return verdict; }
    public String getAgent() { return agent; }
    public String getUserText() { return userText; }
    public String getAssistantText() { return assistantText; }
    public Map<String, Object> getMeta() { return meta; }
  }

  public static class FeedbackAck {
    private final String status;
    private final String receivedAt;
    private final String messageId;

    public FeedbackAck(String status, String receivedAt, String messageId) {
      this.status = status;
      this.receivedAt = receivedAt;
      this.messageId = messageId;
    }
    public String getStatus() { return status; }
    public String getReceivedAt() { return receivedAt; }
    public String getMessageId() { return messageId; }
  }
  
// ---------
  
  @PostMapping(path = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FeedbackAck> submit(@RequestBody FeedbackRequest req) {
    if (req == null || req.getAssistantText() == null || req.getVerdict() == null) {
      return ResponseEntity.badRequest()
          .body(new FeedbackAck("invalid", Instant.now().toString(), req == null ? null : req.getMessageId()));
    }

    String reason = "";
    if (req.getMeta() != null && req.getMeta().get("reason") != null) {
      reason = String.valueOf(req.getMeta().get("reason"));
    }

    String tag = tagReason(reason, req.getAssistantText());

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("ts", Instant.now().toString());                
    event.put("messageId", n(req.getMessageId()));
    event.put("verdict", n(req.getVerdict()));                 
    event.put("agent", n(req.getAgent()));                    
    event.put("reason", safeTruncate(reason, 120));
    event.put("tag", tag);
    event.put("userText", safeTruncate(req.getUserText(), 400));
    event.put("assistantText", safeTruncate(req.getAssistantText(), 1200));
    event.put("meta", req.getMeta() == null ? Map.of() : req.getMeta());
    event.put("reqId", n(MDC.get("reqId")));
    event.put("ip", n(MDC.get("ip")));
    event.put("ua", safeTruncate(MDC.get("ua"), 160));

    try {
      feedbackLog.info(mapper.writeValueAsString(event));  
    } catch (Exception e) {
      log.warn("Failed to write feedback JSON", e);
    }

    return ResponseEntity.ok(new FeedbackAck("ok", Instant.now().toString(), req.getMessageId()));
  }

  // helpers
  private static String n(String s) { return s == null ? "" : s; }

  private static String safeTruncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }

  private static String tagReason(String reason, String answer) {
    String r = reason == null ? "" : reason.toLowerCase();
    String a = answer == null ? "" : answer.toLowerCase();

    if (r.contains("format") || r.contains("bullet") || r.contains("table")) return "formatting";
    if (r.contains("source") || r.contains("policy") || r.contains("link")) return "missing_reference";
    if (r.contains("vague") || r.contains("generic") || r.contains("unclear")) return "clarity";
    if (r.contains("wrong") || r.contains("incorrect") || r.contains("made up") || a.contains("as an ai")) return "hallucination";
    if (r.contains("long")) return "too_long";
    if (r.contains("short")) return "too_short";
    if (r.contains("off") || r.contains("not relevant") || r.contains("outside")) return "off_scope";
    return "unspecified";
  }
}
