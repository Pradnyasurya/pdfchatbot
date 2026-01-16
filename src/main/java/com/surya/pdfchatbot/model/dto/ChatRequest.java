package com.surya.pdfchatbot.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRequest(
        @NotBlank(message = "Document ID is required")
        String documentId,

        @NotBlank(message = "Question is required")
        String question,

        @NotNull(message = "Response format is required")
        ResponseFormat responseFormat
) {
    public enum ResponseFormat {
        TEXT,
        JSON
    }
}
