package com.surya.pdfchatbot.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStatusResponse {
    private String documentId;
    private String filename;
    private String status;
    private Integer pageCount;
    private Integer chunkCount;
    private LocalDateTime uploadDate;
    private String message;
    private String error;
}
