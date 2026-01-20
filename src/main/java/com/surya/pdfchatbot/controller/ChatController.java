package com.surya.pdfchatbot.controller;

import com.surya.pdfchatbot.model.ModelProvider;
import com.surya.pdfchatbot.model.dto.ChatRequest;
import com.surya.pdfchatbot.model.dto.ChatResponse;
import com.surya.pdfchatbot.service.ChatService;
import com.surya.pdfchatbot.service.MultiModelChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final MultiModelChatService multiModelChatService;

    public ChatController(ChatService chatService, MultiModelChatService multiModelChatService) {
        this.chatService = chatService;
        this.multiModelChatService = multiModelChatService;
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

    /**
     * Stream a response for a chat request
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@Valid @RequestBody ChatRequest request) {
        log.info("Received streaming chat request for document: {} with question: {}",
                request.documentId(), request.question());

        return chatService.chatStream(request)
                .doFinally(signalType -> log.info("Streaming chat request completed with signal: {}", signalType));
    }

    /**
     * Get AI model status - shows which models are available and active
     */
    @GetMapping("/models")
    public ResponseEntity<ModelStatusResponse> getModelStatus() {
        ModelProvider activeProvider = multiModelChatService.getActiveProvider();
        List<String> availableProviders = multiModelChatService.getAvailableProviders()
                .stream()
                .map(ModelProvider::getDisplayName)
                .toList();

        return ResponseEntity.ok(new ModelStatusResponse(
                activeProvider != null ? activeProvider.getDisplayName() : "None",
                availableProviders,
                availableProviders.size()
        ));
    }

    /**
     * Response DTO for model status
     */
    public record ModelStatusResponse(
            String activeProvider,
            List<String> availableProviders,
            int totalAvailable
    ) {}
}
