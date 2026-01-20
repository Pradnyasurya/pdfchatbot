package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.model.ModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Service that provides multi-model AI chat capabilities with automatic fallback.
 * If the primary model is unavailable, it automatically tries fallback models in order.
 * 
 * Supported providers:
 * - OpenAI (GPT-4, etc.)
 * - Anthropic (Claude)
 * - Google Gemini (via OpenAI-compatible API)
 */
@Service
public class MultiModelChatService {

    private static final Logger log = LoggerFactory.getLogger(MultiModelChatService.class);

    private final Map<ModelProvider, ChatModel> availableModels;
    private final List<ModelProvider> fallbackOrder;
    private final ModelProvider primaryProvider;
    private final int maxOutputTokens;

    public MultiModelChatService(
            Optional<OpenAiChatModel> openAiChatModel,
            Optional<AnthropicChatModel> anthropicChatModel,
            @Qualifier("geminiChatModel") Optional<ChatModel> geminiChatModel,
            @Value("${app.ai.primary-provider:openai}") String primaryProviderConfig,
            @Value("${app.ai.fallback-order:openai,anthropic,gemini}") String fallbackOrderConfig,
            @Value("${app.ai.max-output-tokens:5000}") int maxOutputTokens) {

        this.maxOutputTokens = maxOutputTokens;
        this.availableModels = new LinkedHashMap<>();

        // Register available models
        openAiChatModel.ifPresent(model -> {
            availableModels.put(ModelProvider.OPENAI, model);
            log.info("OpenAI chat model registered");
        });

        anthropicChatModel.ifPresent(model -> {
            availableModels.put(ModelProvider.ANTHROPIC, model);
            log.info("Anthropic (Claude) chat model registered");
        });

        geminiChatModel.ifPresent(model -> {
            availableModels.put(ModelProvider.GEMINI, model);
            log.info("Google Gemini chat model registered (via OpenAI-compatible API)");
        });

        // Parse fallback order
        this.fallbackOrder = parseFallbackOrder(fallbackOrderConfig);
        this.primaryProvider = ModelProvider.fromConfigKey(primaryProviderConfig);

        log.info("Multi-model chat service initialized. Primary: {}, Fallback order: {}, Max tokens: {}",
                primaryProvider.getDisplayName(),
                fallbackOrder.stream().map(ModelProvider::getDisplayName).toList(),
                maxOutputTokens);

        if (availableModels.isEmpty()) {
            log.warn("No AI models are available! Please configure at least one API key.");
        }
    }

    /**
     * Generate a response using the configured models with automatic fallback.
     *
     * @param promptText The prompt to send to the model
     * @return The generated response
     * @throws ModelUnavailableException if no models are available
     */
    public String chat(String promptText) {
        List<ModelProvider> providersToTry = getProvidersInOrder();

        if (providersToTry.isEmpty()) {
            throw new ModelUnavailableException("No AI models are available. Please configure at least one API key.");
        }

        Exception lastException = null;

        for (ModelProvider provider : providersToTry) {
            if (!availableModels.containsKey(provider)) {
                log.debug("Skipping {} - model not configured", provider.getDisplayName());
                continue;
            }

            try {
                log.debug("Attempting to use {} for chat", provider.getDisplayName());
                String response = callModel(provider, promptText);
                log.info("Successfully generated response using {}", provider.getDisplayName());
                return response;
            } catch (Exception e) {
                log.warn("Failed to get response from {}: {}", provider.getDisplayName(), e.getMessage());
                lastException = e;
                // Continue to next provider
            }
        }

        // All providers failed
        String errorMsg = "All AI providers failed. Last error: " +
                (lastException != null ? lastException.getMessage() : "Unknown error");
        log.error(errorMsg);
        throw new ModelUnavailableException(errorMsg, lastException);
    }

    public Flux<String> streamChat(String promptText) {
        List<ModelProvider> providersToTry = getProvidersInOrder();

        if (providersToTry.isEmpty()) {
            return Flux.error(new ModelUnavailableException(
                    "No AI models are available. Please configure at least one API key."));
        }

        Exception lastException = null;

        for (ModelProvider provider : providersToTry) {
            if (!availableModels.containsKey(provider)) {
                log.debug("Skipping {} - model not configured", provider.getDisplayName());
                continue;
            }

            try {
                log.debug("Attempting to use {} for streaming chat", provider.getDisplayName());
                Flux<String> response = callModelStream(provider, promptText);
                log.info("Successfully started streaming response using {}", provider.getDisplayName());
                return response;
            } catch (Exception e) {
                log.warn("Failed to stream response from {}: {}", provider.getDisplayName(), e.getMessage());
                lastException = e;
            }
        }

        String errorMsg = "All AI providers failed. Last error: " +
                (lastException != null ? lastException.getMessage() : "Unknown error");
        log.error(errorMsg);
        return Flux.error(new ModelUnavailableException(errorMsg, lastException));
    }

    /**
     * Get the currently active model provider.
     */
    public ModelProvider getActiveProvider() {
        for (ModelProvider provider : getProvidersInOrder()) {
            if (availableModels.containsKey(provider)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * Check if a specific provider is available.
     */
    public boolean isProviderAvailable(ModelProvider provider) {
        return availableModels.containsKey(provider);
    }

    /**
     * Get list of all available providers.
     */
    public List<ModelProvider> getAvailableProviders() {
        return new ArrayList<>(availableModels.keySet());
    }

    private String callModel(ModelProvider provider, String promptText) {
        ChatModel model = availableModels.get(provider);
        Prompt prompt = createPromptWithOptions(provider, promptText);
        ChatResponse response = model.call(prompt);

        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new RuntimeException("Empty response from " + provider.getDisplayName());
        }

        return response.getResult().getOutput().getText();
    }

    private Flux<String> callModelStream(ModelProvider provider, String promptText) {
        ChatModel model = availableModels.get(provider);
        Prompt prompt = createPromptWithOptions(provider, promptText);

        try {
            return model.stream(prompt)
                    .map(response -> {
                        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                            return "";
                        }
                        return response.getResult().getOutput().getText();
                    });
        } catch (Exception e) {
            throw new RuntimeException("Streaming failed for " + provider.getDisplayName(), e);
        }
    }

    private Prompt createPromptWithOptions(ModelProvider provider, String promptText) {
        return switch (provider) {
            case OPENAI, GEMINI -> new Prompt(promptText, OpenAiChatOptions.builder()
                    .maxTokens(maxOutputTokens)
                    .build());
            case ANTHROPIC -> new Prompt(promptText, AnthropicChatOptions.builder()
                    .maxTokens(maxOutputTokens)
                    .build());
        };
    }

    private List<ModelProvider> getProvidersInOrder() {
        List<ModelProvider> ordered = new ArrayList<>();
        
        // Add primary provider first if it's in the fallback list
        if (fallbackOrder.contains(primaryProvider)) {
            ordered.add(primaryProvider);
        }
        
        // Add remaining providers in fallback order
        for (ModelProvider provider : fallbackOrder) {
            if (!ordered.contains(provider)) {
                ordered.add(provider);
            }
        }
        
        return ordered;
    }

    private List<ModelProvider> parseFallbackOrder(String config) {
        List<ModelProvider> order = new ArrayList<>();
        if (config == null || config.isBlank()) {
            // Default order
            order.addAll(Arrays.asList(ModelProvider.values()));
            return order;
        }

        String[] parts = config.split(",");
        for (String part : parts) {
            try {
                order.add(ModelProvider.fromConfigKey(part.trim()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid provider in fallback order: {}", part);
            }
        }

        // Add any missing providers at the end
        for (ModelProvider provider : ModelProvider.values()) {
            if (!order.contains(provider)) {
                order.add(provider);
            }
        }

        return order;
    }

    /**
     * Exception thrown when no AI models are available.
     */
    public static class ModelUnavailableException extends RuntimeException {
        public ModelUnavailableException(String message) {
            super(message);
        }

        public ModelUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
