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

        // Sanitize filename to prevent path traversal attacks
        String sanitizedFilename = sanitizeFilename(originalFilename);

        // Validate file is a PDF
        if (!sanitizedFilename.toLowerCase().endsWith(".pdf")) {
            throw new StorageException("Only PDF files are allowed.");
        }

        try {
            // Generate unique document ID
            String documentId = UUID.randomUUID().toString();
            
            // Create directory for this document
            Path documentDir = Paths.get(fileStorageConfig.getUploadDir(), documentId);
            Files.createDirectories(documentDir);

            // Store file with sanitized filename
            Path destinationFile = documentDir.resolve(sanitizedFilename);
            
            // Verify the destination is within the upload directory (defense in depth)
            Path uploadRoot = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize();
            Path normalizedDestination = destinationFile.toAbsolutePath().normalize();
            if (!normalizedDestination.startsWith(uploadRoot)) {
                throw new StorageException("Invalid file path detected.");
            }
            
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Stored file: {} with document ID: {}", sanitizedFilename, documentId);

            return new StoredFileInfo(
                    documentId,
                    sanitizedFilename,
                    destinationFile.toString(),
                    file.getSize()
            );

        } catch (IOException e) {
            throw new StorageException("Failed to store file: " + sanitizedFilename, e);
        }
    }

    /**
     * Sanitize filename to prevent path traversal and other attacks
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            throw new StorageException("Filename cannot be null");
        }
        
        // Remove any path components (handles both Unix and Windows paths)
        String sanitized = Paths.get(filename).getFileName().toString();
        
        // Remove any remaining suspicious characters
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Ensure it doesn't start with a dot (hidden file)
        if (sanitized.startsWith(".")) {
            sanitized = "_" + sanitized.substring(1);
        }
        
        // Ensure reasonable length
        if (sanitized.length() > 255) {
            String extension = "";
            int lastDot = sanitized.lastIndexOf('.');
            if (lastDot > 0) {
                extension = sanitized.substring(lastDot);
                sanitized = sanitized.substring(0, Math.min(lastDot, 250 - extension.length())) + extension;
            } else {
                sanitized = sanitized.substring(0, 255);
            }
        }
        
        if (sanitized.isEmpty() || sanitized.equals(".pdf")) {
            sanitized = "document.pdf";
        }
        
        return sanitized;
    }

    /**
     * Load file as Path
     */
    public Path load(String documentId, String filename) {
        // Validate inputs to prevent path traversal
        if (documentId == null || !documentId.matches("^[a-fA-F0-9-]{36}$")) {
            throw new StorageException("Invalid document ID format.");
        }
        
        String sanitizedFilename = sanitizeFilename(filename);
        Path filePath = Paths.get(fileStorageConfig.getUploadDir(), documentId, sanitizedFilename);
        
        // Verify the path is within the upload directory
        Path uploadRoot = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize();
        Path normalizedPath = filePath.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(uploadRoot)) {
            throw new StorageException("Invalid file path detected.");
        }
        
        if (!Files.exists(filePath)) {
            throw new StorageException("File not found: " + sanitizedFilename);
        }
        return filePath;
    }

    /**
     * Delete document directory and all its contents
     */
    public void delete(String documentId) {
        // Validate document ID to prevent path traversal
        if (documentId == null || !documentId.matches("^[a-fA-F0-9-]{36}$")) {
            throw new StorageException("Invalid document ID format.");
        }
        
        try {
            Path documentDir = Paths.get(fileStorageConfig.getUploadDir(), documentId);
            
            // Verify the path is within the upload directory
            Path uploadRoot = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize();
            Path normalizedDir = documentDir.toAbsolutePath().normalize();
            if (!normalizedDir.startsWith(uploadRoot)) {
                throw new StorageException("Invalid document path detected.");
            }
            
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
        // Validate document ID
        if (documentId == null || !documentId.matches("^[a-fA-F0-9-]{36}$")) {
            return false;
        }
        
        String sanitizedFilename = sanitizeFilename(filename);
        Path filePath = Paths.get(fileStorageConfig.getUploadDir(), documentId, sanitizedFilename);
        
        // Verify the path is within the upload directory
        Path uploadRoot = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize();
        Path normalizedPath = filePath.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(uploadRoot)) {
            return false;
        }
        
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
