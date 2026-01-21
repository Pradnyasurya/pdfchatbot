package com.surya.pdfchatbot.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatHistoryResponse(
        List<ChatHistoryItem> items,
        int page,
        int size,
        long totalElements
) {
    public record ChatHistoryItem(
            String id,
            String documentId,
            String question,
            String answer,
            String responseFormat,
            LocalDateTime createdAt
    ) {
    }
}
