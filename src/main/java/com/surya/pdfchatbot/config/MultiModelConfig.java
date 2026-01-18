package com.surya.pdfchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for multi-model AI support.
 * Configures OpenAI, Anthropic (auto-configured), and Gemini (via OpenAI-compatible API).
 */
@Configuration
public class MultiModelConfig {

    private static final Logger log = LoggerFactory.getLogger(MultiModelConfig.class);

    @Value("${app.ai.max-output-tokens:5000}")
    private int maxOutputTokens;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${app.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta/openai/}")
    private String geminiBaseUrl;

    @Value("${app.ai.gemini.model:gemini-1.5-pro}")
    private String geminiModel;

    /**
     * Creates a Gemini chat model using OpenAI-compatible API.
     * This allows using Gemini with the same interface as OpenAI.
     */
    @Bean
    @Qualifier("geminiChatModel")
    @ConditionalOnProperty(name = "app.ai.gemini.api-key")
    public ChatModel geminiChatModel() {
        log.info("Configuring Gemini chat model via OpenAI-compatible API at {}", geminiBaseUrl);
        
        OpenAiApi geminiApi = OpenAiApi.builder()
                .baseUrl(geminiBaseUrl)
                .apiKey(geminiApiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(geminiModel)
                .temperature(0.3)
                .maxTokens(maxOutputTokens)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(geminiApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * Logs the model configuration status on startup.
     */
    @Bean
    public ModelConfigurationStatus modelConfigurationStatus() {
        boolean openAiAvailable = openAiApiKey != null && !openAiApiKey.isBlank();
        boolean anthropicAvailable = anthropicApiKey != null && !anthropicApiKey.isBlank();
        boolean geminiAvailable = geminiApiKey != null && !geminiApiKey.isBlank();

        log.info("=== AI Model Configuration Status ===");
        log.info("OpenAI:    {}", openAiAvailable ? "CONFIGURED" : "NOT CONFIGURED");
        log.info("Anthropic: {}", anthropicAvailable ? "CONFIGURED" : "NOT CONFIGURED");
        log.info("Gemini:    {}", geminiAvailable ? "CONFIGURED (via OpenAI-compatible API)" : "NOT CONFIGURED");
        log.info("Max Output Tokens: {}", maxOutputTokens);
        log.info("=====================================");

        if (!openAiAvailable && !anthropicAvailable && !geminiAvailable) {
            log.warn("WARNING: No AI models are configured! The application will not be able to answer questions.");
            log.warn("Please set at least one of: OPENAI_API_KEY, ANTHROPIC_API_KEY, or GOOGLE_AI_API_KEY");
        }

        return new ModelConfigurationStatus(openAiAvailable, anthropicAvailable, geminiAvailable);
    }

    /**
     * Record to hold model configuration status.
     */
    public record ModelConfigurationStatus(
            boolean openAiAvailable,
            boolean anthropicAvailable,
            boolean geminiAvailable
    ) {
        public boolean hasAnyModel() {
            return openAiAvailable || anthropicAvailable || geminiAvailable;
        }

        public int availableModelCount() {
            int count = 0;
            if (openAiAvailable) count++;
            if (anthropicAvailable) count++;
            if (geminiAvailable) count++;
            return count;
        }
    }
}
