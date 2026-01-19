# Spring AI PDF Chatbot with RAG

A production-ready Spring Boot application that enables intelligent question-answering on PDF documents using Retrieval-Augmented Generation (RAG) with multi-model AI support (OpenAI, Anthropic Claude, Google Gemini) and PostgreSQL with PgVector for vector storage.

## Features

- **PDF Document Upload**: Upload PDF documents up to 50MB
- **Intelligent Text Processing**: Automatic text extraction, chunking with overlap, and embedding generation
- **Vector Search**: PostgreSQL with PgVector extension for efficient similarity search
- **RAG Pipeline**: Retrieval-Augmented Generation for accurate, context-based answers
- **Multi-Model AI Support**: 
  - OpenAI GPT-4 (primary)
  - Anthropic Claude Sonnet 4 (fallback)
  - Google Gemini 1.5 Pro (fallback)
  - Automatic failover between providers
- **Multi-Document Support**: Isolated document management for multiple users
- **Dual Response Formats**: 
  - TEXT: Simple text responses with page citations
  - JSON: Detailed responses with source references and relevance scores
- **Authentication & Authorization**: Spring Security with role-based access control
- **Async Processing**: Non-blocking document processing
- **RESTful API**: Complete CRUD operations for documents and chat
- **Docker Support**: Easy setup with Docker Compose for PostgreSQL with PgVector

## Architecture

```
┌─────────────────┐
│     Client      │
└────────┬────────┘
         │
         ├─── Upload PDF
         │         ↓
         │    Extract Text (PDFBox)
         │         ↓
         │    Chunk Text (1000 chars, 200 overlap)
         │         ↓
         │    Generate Embeddings (OpenAI text-embedding-3-small)
         │         ↓
         │    Store in PostgreSQL + PgVector
         │
         └─── Ask Question
                   ↓
              Vector Search (Top-10 chunks, threshold 0.5)
                   ↓
              Build Context
                   ↓
              Multi-Model AI Answer Generation
              (OpenAI → Claude → Gemini fallback)
                   ↓
              Response (with citations)
```

## Technology Stack

- **Java 21**
- **Spring Boot 3.5.0**
- **Spring AI 1.1.2**
- **Spring Security** (HTTP Basic Auth)
- **OpenAI GPT-4** (Primary LLM)
- **Anthropic Claude Sonnet 4** (Fallback LLM)
- **Google Gemini 1.5 Pro** (Fallback LLM)
- **OpenAI text-embedding-3-small** (Embeddings)
- **PostgreSQL 16 with PgVector** (Vector Store & Document Metadata)
- **Apache PDFBox 3.0.3** (PDF Processing)
- **Docker & Podman** (Containerization)

## Prerequisites

1. **Java 21 JDK** (with compiler)
   ```bash
   sudo dnf install java-21-openjdk-devel
   ```

2. **Docker/Podman** (for PostgreSQL with PgVector)
   ```bash
   # Verify installation
   docker --version  # or podman --version
   ```

3. **API Keys** (at least one required)
   - **OpenAI API Key**: https://platform.openai.com (required for embeddings)
   - **Anthropic API Key**: https://console.anthropic.com (optional, for Claude fallback)
   - **Google AI API Key**: https://aistudio.google.com (optional, for Gemini fallback)

## Quick Start

### 1. Clone the Repository

```bash
git clone <your-repo-url>
cd pdf-chatbot
```

### 2. Set Up Environment

Create a `.env` file or export environment variables:

```bash
# Required
export OPENAI_API_KEY="sk-your-openai-api-key"

# Optional - for multi-model fallback
export ANTHROPIC_API_KEY="sk-ant-your-anthropic-key"
export GOOGLE_AI_API_KEY="your-google-ai-key"

# Optional - Security (defaults shown)
export SECURITY_ENABLED=true
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=admin123
export USER_USERNAME=user
export USER_PASSWORD=user123
```

### 3. Start Infrastructure Services

**Using Docker:**
```bash
docker-compose up -d
```

**Using Podman:**
```bash
# Create network
podman network create chatbot-network

# Start PostgreSQL with PgVector
podman run -d \
  --name pdf-chatbot-postgres \
  --network chatbot-network \
  -e POSTGRES_DB=pdfchatbot \
  -e POSTGRES_USER=chatbot \
  -e POSTGRES_PASSWORD=chatbot123 \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

Verify services are running:
```bash
# Using Docker
docker ps

# Using Podman
podman ps
```

### 4. Build and Run Application

```bash
# Build
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`

## Authentication

The API uses HTTP Basic Authentication. Default credentials:

| Role | Username | Password | Permissions |
|------|----------|----------|-------------|
| Admin | admin | admin123 | Full access (CRUD + Delete) |
| User | user | user123 | Read + Create (no Delete) |

To disable authentication for development:
```bash
export SECURITY_ENABLED=false
```

## API Endpoints

### Document Management

#### 1. Upload Document
```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -u admin:admin123 \
  -F "file=@/path/to/document.pdf"
```

**Response:**
```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "filename": "document.pdf",
  "status": "PROCESSING",
  "message": "Document uploaded successfully and is being processed",
  "uploadDate": "2026-01-15T12:30:00"
}
```

#### 2. Check Document Status
```bash
curl http://localhost:8080/api/documents/{documentId}/status \
  -u user:user123
```

**Response:**
```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "filename": "document.pdf",
  "status": "READY",
  "pageCount": 36,
  "chunkCount": 142,
  "uploadDate": "2026-01-15T12:30:00",
  "message": "Document is ready for querying",
  "error": null
}
```

#### 3. List All Documents
```bash
curl http://localhost:8080/api/documents \
  -u user:user123
```

**Response:**
```json
{
  "documents": [
    {
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "filename": "document.pdf",
      "status": "READY",
      "pageCount": 36,
      "fileSize": 2048576,
      "uploadDate": "2026-01-15 12:30:00"
    }
  ]
}
```

#### 4. Delete Document (Admin only)
```bash
curl -X DELETE http://localhost:8080/api/documents/{documentId} \
  -u admin:admin123
```

### Chat / Question Answering

#### 1. Ask Question (TEXT Format)
```bash
curl -X POST http://localhost:8080/api/chat \
  -u user:user123 \
  -H "Content-Type: application/json" \
  -d '{
    "documentId": "550e8400-e29b-41d4-a716-446655440000",
    "question": "What is the main topic of this document?",
    "responseFormat": "TEXT"
  }'
```

**Response:**
```json
{
  "answer": "The main topic of this document is Spring Framework architecture and best practices. This is discussed primarily in the introduction section on page 1 and elaborated throughout chapters 2-5. (Page 1, Page 2, Page 3)",
  "format": "TEXT",
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "sources": null
}
```

#### 2. Ask Question (JSON Format with Sources)
```bash
curl -X POST http://localhost:8080/api/chat \
  -u user:user123 \
  -H "Content-Type: application/json" \
  -d '{
    "documentId": "550e8400-e29b-41d4-a716-446655440000",
    "question": "What is dependency injection?",
    "responseFormat": "JSON"
  }'
```

**Response:**
```json
{
  "answer": "Dependency Injection is a design pattern where objects receive their dependencies from external sources rather than creating them internally. In Spring Framework, this is achieved through constructor injection, setter injection, or field injection.",
  "format": "JSON",
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "sources": [
    {
      "pageNumber": 12,
      "content": "Dependency Injection (DI) is a fundamental concept in Spring Framework where the Spring IoC container injects objects' dependencies...",
      "relevanceScore": 0.92
    },
    {
      "pageNumber": 13,
      "content": "There are three types of dependency injection in Spring: constructor injection (recommended), setter injection, and field injection...",
      "relevanceScore": 0.87
    }
  ]
}
```

#### 3. Check AI Model Status
```bash
curl http://localhost:8080/api/chat/models \
  -u user:user123
```

**Response:**
```json
{
  "activeProvider": "OpenAI (GPT-4)",
  "availableProviders": ["OpenAI (GPT-4)", "Anthropic (Claude)", "Google (Gemini)"],
  "totalAvailable": 3
}
```

## Configuration

### application.properties

Key configuration options:

```properties
# Security
app.security.enabled=true
app.security.admin.username=admin
app.security.admin.password=admin123

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/pdfchatbot
spring.datasource.username=chatbot
spring.datasource.password=chatbot123

# Multi-Model AI
app.ai.primary-provider=openai
app.ai.fallback-order=openai,anthropic,gemini

# OpenAI
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4-turbo-preview
spring.ai.openai.chat.options.temperature=0.3

# Anthropic (Claude)
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.chat.options.model=claude-sonnet-4-20250514

# Google Gemini
app.ai.gemini.api-key=${GOOGLE_AI_API_KEY}
app.ai.gemini.model=gemini-1.5-pro

# PgVector
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.dimensions=1536

# File Upload
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# RAG Configuration
app.rag.chunk-size=1000
app.rag.chunk-overlap=200
app.rag.top-k=10
app.rag.similarity-threshold=0.5
```

## RAG Configuration Best Practices

### Chunk Size
- **Current: 1000 characters**
- Optimal for most documents
- Adjust based on document density

### Chunk Overlap
- **Current: 200 characters (20%)**
- Prevents context loss at boundaries
- Increases slightly with chunk size

### Top-K Retrieval
- **Current: 10 chunks**
- Balance between context and token limits
- Increase for complex questions

### Similarity Threshold
- **Current: 0.5**
- Filters irrelevant results
- Lower for broader matches, higher for precision

## Project Structure

```
src/main/java/com/surya/pdfchatbot/
├── PdfChatbotApplication.java
├── config/
│   ├── FileStorageConfig.java
│   ├── MultiModelConfig.java
│   ├── RagConfig.java
│   └── SecurityConfig.java
├── controller/
│   ├── DocumentController.java
│   └── ChatController.java
├── service/
│   ├── DocumentService.java
│   ├── DocumentProcessingService.java
│   ├── FileStorageService.java
│   ├── PdfProcessingService.java
│   ├── ChunkingService.java
│   ├── EmbeddingService.java
│   ├── ChatService.java
│   └── MultiModelChatService.java
├── model/
│   ├── ModelProvider.java
│   ├── entity/
│   │   └── Document.java
│   └── dto/
│       ├── ChatRequest.java
│       ├── ChatResponse.java
│       ├── SourceReference.java
│       ├── DocumentUploadResponse.java
│       ├── DocumentStatusResponse.java
│       └── DocumentListResponse.java
├── repository/
│   └── DocumentRepository.java
└── exception/
    ├── GlobalExceptionHandler.java
    ├── DocumentNotFoundException.java
    ├── DocumentProcessingException.java
    ├── InvalidDocumentException.java
    └── StorageException.java
```

## Development

### Run in Development Mode
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Run Tests
```bash
./mvnw test
```

### Build Production JAR
```bash
./mvnw clean package -DskipTests
java -jar target/pdfchatbot-0.0.1-SNAPSHOT.jar
```

## Troubleshooting

### Issue: "Failed to connect to database"
**Solution:** Verify PostgreSQL with PgVector is running:
```bash
docker ps | grep postgres
```

### Issue: "OpenAI API error"
**Solution:** 
- Verify API key is set: `echo $OPENAI_API_KEY`
- Check API key has sufficient credits
- Ensure you're using the correct model name

### Issue: "Authentication failed"
**Solution:**
- Verify you're using correct credentials
- Check if security is enabled: `app.security.enabled`
- Use `-u username:password` with curl

### Issue: "Java compiler not found"
**Solution:** Install JDK (not just JRE):
```bash
sudo dnf install java-21-openjdk-devel
javac -version  # Verify installation
```

### Issue: "All AI models unavailable"
**Solution:**
- Verify at least one API key is configured
- Check the `/api/chat/models` endpoint for available providers
- Review logs for specific provider errors

## Performance Considerations

- **Async Processing**: Document processing happens asynchronously to avoid blocking API requests
- **Chunking Strategy**: Smart sentence-boundary splitting preserves context
- **Vector Search**: PgVector with HNSW index provides fast similarity search even with large document collections
- **Token Management**: Top-K limits prevent exceeding model context windows
- **Multi-Model Fallback**: Automatic failover ensures high availability

## Security Features

- **HTTP Basic Authentication**: Configurable username/password authentication
- **Role-Based Access Control**: 
  - USER role: Read and create operations
  - ADMIN role: Full access including delete operations
- **File Upload Security**:
  - Path traversal protection
  - Filename sanitization
  - PDF-only file type validation
  - File size limits (50MB)
- **Stateless Sessions**: No server-side session storage
- **Input Validation**: Jakarta Bean Validation on all DTOs

## Future Enhancements

- [ ] JWT token-based authentication
- [ ] Implement conversation history/memory
- [ ] Support for multiple file formats (DOCX, TXT, etc.)
- [ ] Streaming responses via Server-Sent Events (SSE)
- [ ] Document comparison features
- [ ] Automatic document summarization
- [ ] Redis caching for frequently asked questions
- [ ] Kubernetes deployment manifests
- [ ] Rate limiting for API endpoints

## License

This project is licensed under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Author

**Pradnyasurya**
- GitHub: [@Pradnyasurya](https://github.com/Pradnyasurya)
- Email: kuranepradnyesh@gmail.com

## Acknowledgments

- [Spring AI](https://spring.io/projects/spring-ai) for AI integration framework
- [OpenAI](https://openai.com) for GPT-4 and embedding models
- [Anthropic](https://www.anthropic.com/) for Claude models
- [Google AI](https://ai.google.dev/) for Gemini models
- [PgVector](https://github.com/pgvector/pgvector) for vector database extension
- [Apache PDFBox](https://pdfbox.apache.org/) for PDF processing
