package com.surya.pdfchatbot.model.dto;

import java.util.List;

public record ChatResponse(
        String answer,
        String format,
        String documentId,
        List<SourceReference> sources
) {
}
