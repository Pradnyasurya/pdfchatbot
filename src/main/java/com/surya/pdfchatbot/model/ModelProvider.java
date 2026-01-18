package com.surya.pdfchatbot.model;

/**
 * Supported AI model providers for chat operations.
 */
public enum ModelProvider {
    OPENAI("openai", "OpenAI GPT"),
    ANTHROPIC("anthropic", "Anthropic Claude"),
    GEMINI("gemini", "Google Gemini");

    private final String configKey;
    private final String displayName;

    ModelProvider(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parse provider from configuration string.
     * @param value The configuration value (e.g., "openai", "anthropic", "gemini")
     * @return The corresponding ModelProvider
     * @throws IllegalArgumentException if the provider is not recognized
     */
    public static ModelProvider fromConfigKey(String value) {
        if (value == null || value.isBlank()) {
            return OPENAI; // Default to OpenAI
        }
        String normalized = value.trim().toLowerCase();
        for (ModelProvider provider : values()) {
            if (provider.configKey.equals(normalized)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown model provider: " + value + 
            ". Supported providers: openai, anthropic, gemini");
    }
}
