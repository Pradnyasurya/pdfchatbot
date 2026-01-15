package com.surya.pdfchatbot.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceReference {
    private Integer pageNumber;
    private String content;
    private Double relevanceScore;
}
