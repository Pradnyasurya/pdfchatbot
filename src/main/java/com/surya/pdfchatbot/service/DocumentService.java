package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.exception.DocumentNotFoundException;
import com.surya.pdfchatbot.exception.InvalidDocumentException;
import com.surya.pdfchatbot.model.dto.DocumentListResponse;
import com.surya.pdfchatbot.model.dto.DocumentStatusResponse;
import com.surya.pdfchatbot.model.dto.DocumentUploadResponse;
import com.surya.pdfchatbot.model.entity.Document;
import com.surya.pdfchatbot.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final EmbeddingService embeddingService;
    private final DocumentProcessingService documentProcessingService;

    public DocumentService(
            DocumentRepository documentRepository,
            FileStorageService fileStorageService,
            EmbeddingService embeddingService,
            DocumentProcessingService documentProcessingService) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.embeddingService = embeddingService;
        this.documentProcessingService = documentProcessingService;
    }

    /**
     * Upload and process PDF document
     */
    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file) {
        // Validate file
        validateFile(file);

        // Store file
        FileStorageService.StoredFileInfo fileInfo = fileStorageService.store(file);

        // Create document entity
        Document document = new Document();
        document.setId(fileInfo.documentId());
        document.setFilename(fileInfo.originalFilename());
        document.setOriginalFilename(fileInfo.originalFilename());
        document.setFilePath(fileInfo.filePath());
        document.setFileSize(fileInfo.fileSize());
        document.setUploadDate(LocalDateTime.now());
        document.setProcessingStatus(Document.ProcessingStatus.PROCESSING);

        documentRepository.save(document);

        // Schedule async processing to start AFTER the transaction commits
        // This ensures the document is visible in the database when the async method runs
        String documentId = document.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentProcessingService.processDocumentAsync(documentId);
            }
        });

        return new DocumentUploadResponse(
                document.getId(),
                document.getFilename(),
                document.getProcessingStatus().toString(),
                "Document uploaded successfully and is being processed",
                document.getUploadDate()
        );
    }

    /**
     * Get document status
     */
    public DocumentStatusResponse getDocumentStatus(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        return new DocumentStatusResponse(
                document.getId(),
                document.getFilename(),
                document.getProcessingStatus().toString(),
                document.getPageCount(),
                document.getChunkCount(),
                document.getUploadDate(),
                getStatusMessage(document),
                document.getErrorMessage()
        );
    }

    /**
     * List all documents
     */
    public DocumentListResponse listDocuments() {
        List<Document> documents = documentRepository.findAllByOrderByUploadDateDesc();
        
        List<DocumentListResponse.DocumentInfo> documentInfos = documents.stream()
                .map(doc -> new DocumentListResponse.DocumentInfo(
                        doc.getId(),
                        doc.getFilename(),
                        doc.getProcessingStatus().toString(),
                        doc.getPageCount(),
                        doc.getFileSize(),
                        doc.getUploadDate().format(DATE_FORMATTER)
                ))
                .toList();

        return new DocumentListResponse(documentInfos);
    }

    /**
     * Delete document
     */
    @Transactional
    public void deleteDocument(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        // Delete file from storage
        fileStorageService.delete(documentId);

        // Delete embeddings from vector store
        embeddingService.deleteDocumentChunks(documentId);

        // Delete from database
        documentRepository.delete(document);

        log.info("Deleted document: {}", documentId);
    }

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidDocumentException("File is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new InvalidDocumentException("Invalid filename");
        }

        if (!filename.toLowerCase().endsWith(".pdf")) {
            throw new InvalidDocumentException("Only PDF files are allowed");
        }

        long maxSize = 50 * 1024 * 1024; // 50 MB
        if (file.getSize() > maxSize) {
            throw new InvalidDocumentException("File size exceeds maximum limit of 50MB");
        }

        // Validate PDF magic bytes (%PDF-)
        validatePdfContent(file);
    }

    /**
     * Validate that the file content is actually a PDF by checking magic bytes
     */
    private void validatePdfContent(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[5];
            int bytesRead = is.read(header);
            
            if (bytesRead < 5) {
                throw new InvalidDocumentException("File is too small to be a valid PDF");
            }
            
            // PDF files start with "%PDF-"
            String headerStr = new String(header);
            if (!headerStr.equals("%PDF-")) {
                throw new InvalidDocumentException("Invalid PDF file: file does not have valid PDF header. Please upload a valid PDF document.");
            }
        } catch (IOException e) {
            throw new InvalidDocumentException("Failed to read file content: " + e.getMessage());
        }
    }

    /**
     * Get human-readable status message
     */
    private String getStatusMessage(Document document) {
        return switch (document.getProcessingStatus()) {
            case UPLOADING -> "Document is being uploaded";
            case PROCESSING -> "Document is being processed";
            case READY -> "Document is ready for querying";
            case FAILED -> "Document processing failed";
        };
    }
}
