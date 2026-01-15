# AGENTS.md - AI Coding Agent Instructions

This document provides guidelines for AI coding agents working in this repository.

## Project Overview

Spring Boot 3.5.0 RAG (Retrieval-Augmented Generation) application for PDF document question-answering.

**Tech Stack:**
- Java 21 with modern features (records, switch expressions, text blocks)
- Spring Boot 3.5.0 with Spring AI 1.1.2
- PostgreSQL 16 with PgVector for vector storage
- OpenAI GPT-4 for chat, text-embedding-3-small for embeddings
- Apache PDFBox 3.0.3 for PDF extraction
- Lombok for boilerplate reduction
- Maven for build management

## Build/Lint/Test Commands

### Build Commands

```bash
# Build without tests
./mvnw clean package -DskipTests

# Build with tests
./mvnw clean package

# Run application
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run production JAR
java -jar target/pdfchatbot-0.0.1-SNAPSHOT.jar
```

### Test Commands

```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=PdfChatbotApplicationTests

# Run a specific test method
./mvnw test -Dtest=PdfChatbotApplicationTests#contextLoads

# Run tests matching a pattern
./mvnw test -Dtest="*Service*"

# Run tests with verbose output
./mvnw test -X
```

### Infrastructure

```bash
# Start PostgreSQL and ChromaDB containers
docker-compose up -d

# Use convenience startup script (loads .env)
./start.sh
```

## Project Structure

```
src/main/java/com/surya/pdfchatbot/
├── PdfChatbotApplication.java    # Entry point (@EnableAsync)
├── config/                       # Configuration classes
├── controller/                   # REST endpoints
├── service/                      # Business logic
├── repository/                   # Data access (JPA)
├── model/
│   ├── dto/                      # Data Transfer Objects
│   └── entity/                   # JPA Entities
└── exception/                    # Custom exceptions + global handler
```

## Code Style Guidelines

### Package and Import Conventions

**Import ordering:**
1. Project imports (`com.surya.pdfchatbot.*`)
2. Jakarta EE imports (`jakarta.*`)
3. External libraries (`org.slf4j.*`, `org.springframework.*`)
4. Java standard library (`java.*`)

### Dependency Injection

Always use **constructor injection**, not field injection:

```java
// CORRECT
private final ChatService chatService;

public ChatController(ChatService chatService) {
    this.chatService = chatService;
}

// INCORRECT - do not use
@Autowired
private ChatService chatService;
```

### Logging

Use SLF4J with LoggerFactory:

```java
private static final Logger log = LoggerFactory.getLogger(ClassName.class);

log.info("Processing document: {}", documentId);
log.error("Failed to process document", exception);
```

### DTOs and Data Classes

Use Lombok annotations for DTOs:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    @NotBlank(message = "Question is required")
    private String question;
    private String documentId;
}
```

Use Java records for immutable internal data structures:

```java
private record RetrievedChunk(String content, Map<String, Object> metadata, double score) {}
```

### Entity Classes

```java
@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### REST Controllers

```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @PostMapping
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        // Implementation
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentStatusResponse> getDocumentStatus(
            @PathVariable String id) {
        // Implementation
    }
}
```

### Exception Handling

Create custom runtime exceptions:

```java
public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String message) {
        super(message);
    }
}
```

Use global exception handler with `@RestControllerAdvice`:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    public record ErrorResponse(String error, String message, int status) {}

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("Not Found", ex.getMessage(), 404));
    }
}
```

### Modern Java Features (Java 21)

Use switch expressions:
```java
return switch (document.getStatus()) {
    case PENDING -> "Waiting to process";
    case PROCESSING -> "Currently processing";
    case COMPLETED -> "Ready for queries";
    case FAILED -> "Processing failed";
};
```

Use text blocks for multi-line strings (especially prompts):
```java
String prompt = """
    You are a helpful assistant that answers questions about documents.
    Use the following context to answer the question.
    
    Context: %s
    Question: %s
    """.formatted(context, question);
```

Use Stream API with `.toList()`:
```java
List<String> names = documents.stream()
    .map(Document::getName)
    .toList();
```

### Configuration

Use `@Value` for property injection:

```java
@Configuration
public class RagConfig {
    @Value("${app.rag.chunk-size:1000}")
    private int chunkSize;
}
```

Custom properties use `app.` prefix in `application.properties`.

### Async Processing

For async operations, use `@Async` with proper transaction handling:

```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            documentProcessingService.processDocumentAsync(document.getId());
        }
    }
);
```

## API Conventions

- REST endpoints under `/api/` prefix
- Document CRUD: `/api/documents/*`
- Chat endpoint: `/api/chat`
- Use `ResponseEntity<T>` for all controller responses
- Validate requests with `@Valid` and Jakarta Bean Validation

## Environment Setup

Required environment variables (in `.env`):
- `OPENAI_API_KEY` - OpenAI API key for embeddings and chat
- Database connection configured in `application.properties`

## Testing Guidelines

- Use `@SpringBootTest` for integration tests
- Test classes go in `src/test/java/com/surya/pdfchatbot/`
- Mirror the main source package structure
- Name test classes with `*Tests.java` suffix
