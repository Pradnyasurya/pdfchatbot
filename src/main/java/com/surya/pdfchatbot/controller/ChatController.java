package com.surya.pdfchatbot.controller;

import com.surya.pdfchatbot.model.dto.ChatRequest;
import com.surya.pdfchatbot.model.dto.ChatResponse;
import com.surya.pdfchatbot.service.ChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Ask a question about the document
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request for document: {} with question: {}",
                request.documentId(), request.question());
        
        ChatResponse response = chatService.chat(request);
        
        log.info("Generated response in {} format", request.responseFormat());
        
        return ResponseEntity.ok(response);
    }
}
