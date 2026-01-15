package com.surya.pdfchatbot.service;

import com.surya.pdfchatbot.config.FileStorageConfig;
import com.surya.pdfchatbot.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private final FileStorageConfig fileStorageConfig;

    public FileStorageService(FileStorageConfig fileStorageConfig) {
        this.fileStorageConfig = fileStorageConfig;
    }

    /**
     * Store uploaded file with a unique document ID
     */
    public StoredFileInfo store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new StorageException("Failed to store empty file.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new StorageException("Invalid file name.");
        }

        // Validate file is a PDF
        if (!originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new StorageException("Only PDF files are allowed.");
        }

        try {
            // Generate unique document ID
            String documentId = UUID.randomUUID().toString();
            
            // Create directory for this document
            Path documentDir = Paths.get(fileStorageConfig.getUploadDir(), documentId);
            Files.createDirectories(documentDir);

            // Store file
            Path destinationFile = documentDir.resolve(originalFilename);
            
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Stored file: {} with document ID: {}", originalFilename, documentId);

            return new StoredFileInfo(
                    documentId,
                    originalFilename,
                    destinationFile.toString(),
                    file.getSize()
            );

        } catch (IOException e) {
            throw new StorageException("Failed to store file: " + originalFilename, e);
        }
    }

    /**
     * Load file as Path
     */
    public Path load(String documentId, String filename) {
        Path filePath = Paths.get(fileStorageConfig.getUploadDir(), documentId, filename);
        if (!Files.exists(filePath)) {
            throw new StorageException("File not found: " + filename);
        }
        return filePath;
    }

    /**
     * Delete document directory and all its contents
     */
    public void delete(String documentId) {
        try {
            Path documentDir = Paths.get(fileStorageConfig.getUploadDir(), documentId);
            if (Files.exists(documentDir)) {
                Files.walk(documentDir)
                        .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.error("Failed to delete: {}", path, e);
                            }
                        });
                log.info("Deleted document directory: {}", documentId);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to delete document: " + documentId, e);
        }
    }

    /**
     * Check if file exists
     */
    public boolean exists(String documentId, String filename) {
        Path filePath = Paths.get(fileStorageConfig.getUploadDir(), documentId, filename);
        return Files.exists(filePath);
    }

    /**
     * DTO for stored file information
     */
    public record StoredFileInfo(
            String documentId,
            String originalFilename,
            String filePath,
            long fileSize
    ) {}
}
