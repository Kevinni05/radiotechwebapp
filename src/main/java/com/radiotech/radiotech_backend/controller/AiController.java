package com.radiotech.radiotech_backend.controller;
import com.radiotech.radiotech_backend.dto.AiChatRequest;
import com.radiotech.radiotech_backend.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private final OperationalInsightsService insights; private final AiConversationService chat;
    public AiController(OperationalInsightsService insights, AiConversationService chat) { this.insights = insights; this.chat = chat; }
    @GetMapping("/insights") public Map<String, Object> insights() throws Exception { return insights.insights(); }
    @GetMapping("/status") public Map<String, Object> status() { return chat.status(); }
    @PostMapping("/chat") public Map<String, Object> chat(@Valid @RequestBody AiChatRequest request) throws Exception { return chat.chat(request); }
}
