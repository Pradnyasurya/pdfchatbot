package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.config.RagConfig;
import com.surya.pdfchatbot.exception.DocumentNotFoundException;
import com.surya.pdfchatbot.exception.DocumentProcessingException;
import com.surya.pdfchatbot.model.dto.ChatRequest;
import com.surya.pdfchatbot.model.dto.ChatResponse;
import com.surya.pdfchatbot.model.dto.SourceReference;
import com.surya.pdfchatbot.model.entity.AppUser;
import com.surya.pdfchatbot.model.entity.ChatMessage;
import com.surya.pdfchatbot.model.entity.Document;
import com.surya.pdfchatbot.repository.ChatMessageRepository;
import com.surya.pdfchatbot.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT = """
            You are a helpful AI assistant that answers questions based ONLY on the provided document context.
            
            Follow these rules strictly:
            1. Only use information from the provided context to answer questions
            2. If the answer is not in the context, respond with: "I cannot find this information in the document."
            3. Always cite the page number(s) where you found the information
            4. Be concise but comprehensive in your answers
            5. If the context is ambiguous or unclear, acknowledge it
            6. Do not make assumptions or add information not present in the context
            
            When you provide an answer, reference the page numbers like this: (Page X).
            """;

    private static final String PROMPT_TEMPLATE = """
            {system_prompt}
            
            Context:
            {context}
            
            Question: {question}
            
            Answer:
            """;

    private final MultiModelChatService multiModelChatService;
    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final RagConfig ragConfig;
    private final ChatMessageRepository chatMessageRepository;

    public ChatService(
            MultiModelChatService multiModelChatService,
            VectorStore vectorStore,
            DocumentRepository documentRepository,
            RagConfig ragConfig,
            ChatMessageRepository chatMessageRepository) {
        this.multiModelChatService = multiModelChatService;
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.ragConfig = ragConfig;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Process chat question with RAG
     */
    public ChatResponse chat(ChatRequest request, AppUser user) {
        ensureUserPresent(user);

        // Validate document exists and is ready
        Document document = documentRepository.findById(request.documentId())
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + request.documentId()));

        if (document.getProcessingStatus() != Document.ProcessingStatus.READY) {
            throw new DocumentProcessingException("Document is not ready for querying. Status: " +
                    document.getProcessingStatus());
        }

        try {
            ChatContext chatContext = buildChatContext(request.documentId(), request.question());

            if (chatContext.relevantChunks().isEmpty()) {
                String answer = "I cannot find relevant information in the document to answer your question.";
                persistChatMessage(user, request, answer);
                return new ChatResponse(
                        answer,
                        request.responseFormat().toString(),
                        request.documentId(),
                        List.of()
                );
            }

            // Generate answer using LLM
            String answer = generateAnswer(request.question(), chatContext.context());

            // Prepare sources for JSON response
            List<SourceReference> sources = request.responseFormat() == ChatRequest.ResponseFormat.JSON ?
                    buildSourceReferences(chatContext.relevantChunks()) : null;

            persistChatMessage(user, request, answer);

            return new ChatResponse(answer, request.responseFormat().toString(), request.documentId(), sources);

        } catch (Exception e) {
            log.error("Error processing chat request", e);
            throw new DocumentProcessingException("Failed to process question: " + e.getMessage(), e);
        }
    }

    public Flux<String> chatStream(ChatRequest request, AppUser user) {
        ensureUserPresent(user);

        try {
            ChatContext chatContext = buildChatContext(request.documentId(), request.question());

            if (chatContext.relevantChunks().isEmpty()) {
                String answer = "I cannot find relevant information in the document to answer your question.";
                persistChatMessage(user, request, answer);
                return Flux.just(answer);
            }

            return generateAnswerStream(request.question(), chatContext.context())
                    .collectList()
                    .flatMapMany(chunks -> {
                        String answer = String.join("", chunks).trim();
                        persistChatMessage(user, request, answer);
                        return Flux.fromIterable(chunks);
                    });
        } catch (Exception e) {
            log.error("Error processing streaming chat request", e);
            return Flux.error(new DocumentProcessingException("Failed to process question: " + e.getMessage(), e));
        }
    }

    public Flux<String> chatStream(ChatRequest request) {
        try {
            ChatContext chatContext = buildChatContext(request.documentId(), request.question());

            if (chatContext.relevantChunks().isEmpty()) {
                return Flux.just("I cannot find relevant information in the document to answer your question.");
            }

            return generateAnswerStream(request.question(), chatContext.context());
        } catch (Exception e) {
            log.error("Error processing streaming chat request", e);
            return Flux.error(new DocumentProcessingException("Failed to process question: " + e.getMessage(), e));
        }
    }

    /**
     * Retrieve relevant document chunks using vector similarity search
     */
    private List<RetrievedChunk> retrieveRelevantChunks(String documentId, String question) {
        try {
            // Create filter for specific document
            var filterExpression = new FilterExpressionBuilder()
                    .eq("documentId", documentId)
                    .build();

            log.debug("Searching for chunks with documentId={}, topK={}, threshold={}", 
                    documentId, ragConfig.getTopK(), ragConfig.getSimilarityThreshold());

            // Debug: First try without filter to see if ANY chunks exist
            SearchRequest debugRequest = SearchRequest.builder()
                    .query(question)
                    .topK(5)
                    .similarityThreshold(0.0)
                    .build();
            List<org.springframework.ai.document.Document> debugResults = vectorStore.similaritySearch(debugRequest);
            log.debug("DEBUG: Vector store contains {} total chunks (unfiltered search)", debugResults.size());
            if (!debugResults.isEmpty()) {
                log.debug("DEBUG: First chunk metadata: {}", debugResults.get(0).getMetadata());
            }

            // Search for similar chunks
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(question)
                    .topK(ragConfig.getTopK())
                    .similarityThreshold(ragConfig.getSimilarityThreshold())
                    .filterExpression(filterExpression)
                    .build();

            List<org.springframework.ai.document.Document> results = vectorStore.similaritySearch(searchRequest);
            
            log.debug("Vector store returned {} results", results.size());

            List<RetrievedChunk> chunks = new ArrayList<>();
            for (org.springframework.ai.document.Document doc : results) {
                Map<String, Object> metadata = doc.getMetadata();
                log.debug("Chunk metadata: {}", metadata);
                
                // Safely extract metadata with null checks
                Integer pageNumber = metadata != null ? (Integer) metadata.get("pageNumber") : null;
                Integer chunkIndex = metadata != null ? (Integer) metadata.get("chunkIndex") : null;
                
                chunks.add(new RetrievedChunk(
                        doc.getText(),
                        pageNumber != null ? pageNumber : 0,
                        chunkIndex != null ? chunkIndex : 0,
                        0.85 // Default similarity - actual score might not be available
                ));
            }

            log.info("Retrieved {} relevant chunks for question", chunks.size());
            return chunks;

        } catch (Exception e) {
            log.error("Error retrieving relevant chunks", e);
            throw new DocumentProcessingException("Failed to retrieve relevant information", e);
        }
    }

    /**
     * Build context string from retrieved chunks
     */
    private String buildContext(List<RetrievedChunk> chunks) {
        StringBuilder context = new StringBuilder();
        context.append("Based on the following excerpts from the document:\n\n");

        for (RetrievedChunk chunk : chunks) {
            context.append(String.format("[Page %d] %s\n\n",
                    chunk.pageNumber(),
                    chunk.content()));
        }

        return context.toString();
    }

    /**
     * Generate answer using LLM with RAG context (with multi-model fallback support)
     */
    private String generateAnswer(String question, String context) {
        PromptTemplate promptTemplate = createPromptTemplate(question, context);

        try {
            String response = multiModelChatService.chat(promptTemplate.render());
            return response != null ? response.trim() : "Unable to generate answer.";
        } catch (MultiModelChatService.ModelUnavailableException e) {
            log.error("All AI models failed to generate response", e);
            throw new DocumentProcessingException("Failed to generate answer: " + e.getMessage(), e);
        }
    }

    private Flux<String> generateAnswerStream(String question, String context) {
        PromptTemplate promptTemplate = createPromptTemplate(question, context);

        try {
            return multiModelChatService.streamChat(promptTemplate.render())
                    .map(chunk -> chunk == null ? "" : chunk);
        } catch (MultiModelChatService.ModelUnavailableException e) {
            log.error("All AI models failed to stream response", e);
            return Flux.error(new DocumentProcessingException("Failed to generate answer: " + e.getMessage(), e));
        }
    }

    private PromptTemplate createPromptTemplate(String question, String context) {
        PromptTemplate promptTemplate = new PromptTemplate(PROMPT_TEMPLATE);
        promptTemplate.add("system_prompt", SYSTEM_PROMPT);
        promptTemplate.add("context", context);
        promptTemplate.add("question", question);
        return promptTemplate;
    }

    private ChatContext buildChatContext(String documentId, String question) {
        // Validate document exists and is ready
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        if (document.getProcessingStatus() != Document.ProcessingStatus.READY) {
            throw new DocumentProcessingException("Document is not ready for querying. Status: " +
                    document.getProcessingStatus());
        }

        // Retrieve relevant chunks using vector similarity search
        List<RetrievedChunk> relevantChunks = retrieveRelevantChunks(documentId, question);

        // Build context from relevant chunks
        String context = buildContext(relevantChunks);

        return new ChatContext(relevantChunks, context);
    }

    private void persistChatMessage(AppUser user, ChatRequest request, String answer) {
        if (user == null) {
            return;
        }
        ChatMessage message = new ChatMessage(
                user,
                request.documentId(),
                request.question(),
                answer,
                ChatMessage.ChatResponseFormat.valueOf(request.responseFormat().name())
        );
        chatMessageRepository.save(message);
    }

    private void ensureUserPresent(AppUser user) {
        if (user == null) {
            throw new DocumentProcessingException("Authenticated user is required for chat requests");
        }
    }

    /**
     * Build source references for JSON response
     */
    private List<SourceReference> buildSourceReferences(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> {
                    String content = chunk.content();
                    String truncatedContent = content.length() > 200 
                            ? content.substring(0, 200) + "..." 
                            : content;
                    return new SourceReference(
                            chunk.pageNumber(),
                            truncatedContent,
                            chunk.relevanceScore()
                    );
                })
                .toList();
    }

    /**
     * DTO for retrieved chunk with metadata
     */
    private record RetrievedChunk(
            String content,
            int pageNumber,
            int chunkIndex,
            double relevanceScore
    ) {}

    private record ChatContext(
            List<RetrievedChunk> relevantChunks,
            String context
    ) {}
}
