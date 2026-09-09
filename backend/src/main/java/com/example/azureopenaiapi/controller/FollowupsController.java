package com.example.azureopenaiapi.controller;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:4200"}, allowCredentials = "true")
public class FollowupsController {

  private final OpenAIClient openAI;
  private final String deployment;

  public FollowupsController(OpenAIClient openAI, @Value("${azure.openai.deployment}") String deployment) {
    this.openAI = openAI;
    this.deployment = deployment;
  }

  public record FollowupsRequest(String user, String answer) {}
  public record FollowupsReply(List<String> followUps) {}

  @PostMapping(
      path = "/followups",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  
  public FollowupsReply followups(@RequestBody FollowupsRequest req) {
    try {
    	// In FollowupsController.followups()
    	String system = """
    	  You generate 3 short, actionable SUGGESTIONS the user can click.
    	  IMPORTANT: Output as first-person user prompts (not questions), e.g. "I need help with ..."
    	  Keep each under 12 words, no punctuation at the end, no numbering.
    	  Return ONLY JSON: {"followUps": ["...","...","..."]}
    	""";

      String user = """
          User asked: "%s"
          Assistant answered: "%s"
          """.formatted(nullToEmpty(req.user()), nullToEmpty(req.answer()));
      
      System.out.println("system is " +system);
      System.out.println("user is "+ user);
      
      ChatCompletionsOptions opts = new ChatCompletionsOptions(
          List.of(new ChatRequestSystemMessage(system),
                  new ChatRequestUserMessage(user))
      );

      ChatCompletions cc = openAI.getChatCompletions(deployment, opts);
      String raw = cc.getChoices().get(0).getMessage().getContent();
      
      System.out.println("raw is "+ raw);
      
      List<String> ups = JsonUtils.tryExtractFollowUps(raw);
      
     
      
      return new FollowupsReply(ups);
    } catch (Exception e) {
      return new FollowupsReply(List.of(
          "Open the correct form",
          "Who should I contact",
          "Show me an example"
      ));
    }
  }

  private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
