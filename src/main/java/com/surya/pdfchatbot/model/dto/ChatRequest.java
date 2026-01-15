package com.surya.pdfchatbot.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Document ID is required")
    private String documentId;

    @NotBlank(message = "Question is required")
    private String question;

    @NotNull(message = "Response format is required")
    private ResponseFormat responseFormat;

    public enum ResponseFormat {
        TEXT,
        JSON
    }
}
