package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.config.RagConfig;
import com.surya.pdfchatbot.exception.DocumentNotFoundException;
import com.surya.pdfchatbot.exception.DocumentProcessingException;
import com.surya.pdfchatbot.model.dto.ChatRequest;
import com.surya.pdfchatbot.model.dto.ChatResponse;
import com.surya.pdfchatbot.model.dto.SourceReference;
import com.surya.pdfchatbot.model.entity.Document;
import com.surya.pdfchatbot.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

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

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final RagConfig ragConfig;

    public ChatService(
            ChatClient.Builder chatClientBuilder,
            VectorStore vectorStore,
            DocumentRepository documentRepository,
            RagConfig ragConfig) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.ragConfig = ragConfig;
    }

    /**
     * Process chat question with RAG
     */
    public ChatResponse chat(ChatRequest request) {
        // Validate document exists and is ready
        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + request.getDocumentId()));

        if (document.getProcessingStatus() != Document.ProcessingStatus.READY) {
            throw new DocumentProcessingException("Document is not ready for querying. Status: " + 
                    document.getProcessingStatus());
        }

        try {
            // Retrieve relevant chunks using vector similarity search
            List<RetrievedChunk> relevantChunks = retrieveRelevantChunks(
                    request.getDocumentId(),
                    request.getQuestion()
            );

            if (relevantChunks.isEmpty()) {
                return createResponse(
                        "I cannot find relevant information in the document to answer your question.",
                        request.getResponseFormat().toString(),
                        request.getDocumentId(),
                        List.of()
                );
            }

            // Build context from relevant chunks
            String context = buildContext(relevantChunks);

            // Generate answer using LLM
            String answer = generateAnswer(request.getQuestion(), context);

            // Prepare sources for JSON response
            List<SourceReference> sources = request.getResponseFormat() == ChatRequest.ResponseFormat.JSON ?
                    buildSourceReferences(relevantChunks) : null;

            return createResponse(answer, request.getResponseFormat().toString(), request.getDocumentId(), sources);

        } catch (Exception e) {
            log.error("Error processing chat request", e);
            throw new DocumentProcessingException("Failed to process question: " + e.getMessage(), e);
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

            // Search for similar chunks
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(question)
                    .topK(ragConfig.getTopK())
                    .similarityThreshold(ragConfig.getSimilarityThreshold())
                    .filterExpression(filterExpression)
                    .build();

            List<org.springframework.ai.document.Document> results = vectorStore.similaritySearch(searchRequest);

            List<RetrievedChunk> chunks = new ArrayList<>();
            for (org.springframework.ai.document.Document doc : results) {
                Map<String, Object> metadata = doc.getMetadata();
                chunks.add(new RetrievedChunk(
                        doc.getText(),
                        (Integer) metadata.get("pageNumber"),
                        (Integer) metadata.get("chunkIndex"),
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
     * Generate answer using LLM with RAG context
     */
    private String generateAnswer(String question, String context) {
        String promptText = """
                {system_prompt}
                
                Context:
                {context}
                
                Question: {question}
                
                Answer:
                """;

        PromptTemplate promptTemplate = new PromptTemplate(promptText);
        promptTemplate.add("system_prompt", SYSTEM_PROMPT);
        promptTemplate.add("context", context);
        promptTemplate.add("question", question);

        String response = chatClient.prompt()
                .user(promptTemplate.render())
                .call()
                .content();

        return response != null ? response.trim() : "Unable to generate answer.";
    }

    /**
     * Build source references for JSON response
     */
    private List<SourceReference> buildSourceReferences(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> new SourceReference(
                        chunk.pageNumber(),
                        chunk.content().substring(0, Math.min(chunk.content().length(), 200)) + "...",
                        chunk.relevanceScore()
                ))
                .toList();
    }

    /**
     * Create chat response
     */
    private ChatResponse createResponse(String answer, String format, String documentId, List<SourceReference> sources) {
        ChatResponse response = new ChatResponse();
        response.setAnswer(answer);
        response.setFormat(format);
        response.setDocumentId(documentId);
        response.setSources(sources);
        return response;
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
}
