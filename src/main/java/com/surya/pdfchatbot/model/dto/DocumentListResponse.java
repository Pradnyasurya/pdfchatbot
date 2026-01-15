package com.surya.pdfchatbot.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentListResponse {
    private List<DocumentInfo> documents;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentInfo {
        private String documentId;
        private String filename;
        private String status;
        private Integer pageCount;
        private Long fileSize;
        private String uploadDate;
    }
}
