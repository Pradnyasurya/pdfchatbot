package com.surya.pdfchatbot.model.dto;

import java.time.LocalDateTime;

public record DocumentStatusResponse(
        String documentId,
        String filename,
        String status,
        Integer pageCount,
        Integer chunkCount,
        LocalDateTime uploadDate,
        String message,
        String error
) {
}
