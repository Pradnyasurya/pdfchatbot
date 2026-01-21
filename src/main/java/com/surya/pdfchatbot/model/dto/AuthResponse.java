package com.surya.pdfchatbot.model.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds
) {
}
