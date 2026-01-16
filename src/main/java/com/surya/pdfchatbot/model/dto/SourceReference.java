package com.surya.pdfchatbot.model.dto;

public record SourceReference(
        Integer pageNumber,
        String content,
        Double relevanceScore
) {
}
