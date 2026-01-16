package com.surya.pdfchatbot.model.dto;

import java.time.LocalDateTime;

public record DocumentUploadResponse(
        String documentId,
        String filename,
        String status,
        String message,
        LocalDateTime uploadDate
) {
}
