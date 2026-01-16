package com.surya.pdfchatbot.model.dto;

import java.util.List;

public record DocumentListResponse(List<DocumentInfo> documents) {

    public record DocumentInfo(
            String documentId,
            String filename,
            String status,
            Integer pageCount,
            Long fileSize,
            String uploadDate
    ) {
    }
}
