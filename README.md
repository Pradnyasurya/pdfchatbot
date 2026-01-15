# Spring AI PDF Chatbot with RAG

A production-ready Spring Boot application that enables intelligent question-answering on PDF documents using Retrieval-Augmented Generation (RAG) with OpenAI and ChromaDB vector store.

## Features

- **PDF Document Upload**: Upload PDF documents up to 50MB
- **Intelligent Text Processing**: Automatic text extraction, chunking with overlap, and embedding generation
- **Vector Search**: ChromaDB vector store for efficient similarity search
- **RAG Pipeline**: Retrieval-Augmented Generation for accurate, context-based answers
- **Multi-Document Support**: Isolated document management for multiple users
- **Dual Response Formats**: 
  - TEXT: Simple text responses with page citations
  - JSON: Detailed responses with source references and relevance scores
- **Async Processing**: Non-blocking document processing
- **RESTful API**: Complete CRUD operations for documents and chat
- **Docker Support**: Easy setup with Docker Compose for PostgreSQL and ChromaDB

## Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ├─── Upload PDF
       │         ↓
       │    Extract Text (PDFBox)
       │         ↓
       │    Chunk Text (1000 chars, 200 overlap)
       │         ↓
       │    Generate Embeddings (OpenAI)
       │         ↓
       │    Store in ChromaDB + PostgreSQL
       │
       └─── Ask Question
                 ↓
            Vector Search (Top-4 chunks)
                 ↓
            Build Context
                 ↓
            GPT-4 Answer Generation
                 ↓
            Response (with citations)
```

## Technology Stack

- **Java 21**
- **Spring Boot 3.5.0**
- **Spring AI 1.0.0-M5**
- **OpenAI GPT-4** (LLM)
- **OpenAI text-embedding-3-small** (Embeddings)
- **ChromaDB** (Vector Store)
- **PostgreSQL 16** (Document Metadata)
- **Apache PDFBox 3.0.3** (PDF Processing)
- **Docker & Podman** (Containerization)

## Prerequisites

1. **Java 21 JDK** (with compiler)
   ```bash
   sudo dnf install java-21-openjdk-devel
   ```

2. **Docker/Podman** (for PostgreSQL and ChromaDB)
   ```bash
   # Verify installation
   docker --version  # or podman --version
   ```

3. **OpenAI API Key**
   - Sign up at https://platform.openai.com
   - Generate an API key

## Quick Start

### 1. Clone the Repository

```bash
git clone <your-repo-url>
cd pdf-chatbot
```

### 2. Set Up Environment

Set your OpenAI API key:

```bash
export OPENAI_API_KEY="sk-your-api-key-here"
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

# Start PostgreSQL
podman run -d \
  --name pdf-chatbot-postgres \
  --network chatbot-network \
  -e POSTGRES_DB=pdfchatbot \
  -e POSTGRES_USER=chatbot \
  -e POSTGRES_PASSWORD=chatbot123 \
  -p 5432:5432 \
  docker.io/library/postgres:16-alpine

# Start ChromaDB
podman run -d \
  --name pdf-chatbot-chroma \
  --network chatbot-network \
  -e ANONYMIZED_TELEMETRY=False \
  -e ALLOW_RESET=True \
  -p 8000:8000 \
  docker.io/chromadb/chroma:latest
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

## API Endpoints

### Document Management

#### 1. Upload Document
```bash
curl -X POST http://localhost:8080/api/documents/upload \
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
curl http://localhost:8080/api/documents/{documentId}/status
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
curl http://localhost:8080/api/documents
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

#### 4. Delete Document
```bash
curl -X DELETE http://localhost:8080/api/documents/{documentId}
```

### Chat / Question Answering

#### 1. Ask Question (TEXT Format)
```bash
curl -X POST http://localhost:8080/api/chat \
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

## Configuration

### application.properties

Key configuration options:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/pdfchatbot
spring.datasource.username=chatbot
spring.datasource.password=chatbot123

# OpenAI
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4-turbo-preview
spring.ai.openai.chat.options.temperature=0.3

# ChromaDB
spring.ai.vectorstore.chroma.client.host=localhost
spring.ai.vectorstore.chroma.client.port=8000

# File Upload
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# RAG Configuration
app.rag.chunk-size=1000
app.rag.chunk-overlap=200
app.rag.top-k=4
app.rag.similarity-threshold=0.7
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
- **Current: 4 chunks**
- Balance between context and token limits
- Increase for complex questions

### Similarity Threshold
- **Current: 0.7**
- Filters irrelevant results
- Lower for broader matches

## Project Structure

```
src/main/java/com/surya/pdfchatbot/
├── PdfChatbotApplication.java
├── config/
│   ├── FileStorageConfig.java
│   └── RagConfig.java
├── controller/
│   ├── DocumentController.java
│   └── ChatController.java
├── service/
│   ├── DocumentService.java
│   ├── FileStorageService.java
│   ├── PdfProcessingService.java
│   ├── ChunkingService.java
│   ├── EmbeddingService.java
│   └── ChatService.java
├── model/
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

### Issue: "Failed to connect to ChromaDB"
**Solution:** Ensure ChromaDB container is running:
```bash
podman ps | grep chroma  # or docker ps
```

### Issue: "Failed to connect to database"
**Solution:** Verify PostgreSQL is running and credentials are correct:
```bash
podman ps | grep postgres
```

### Issue: "OpenAI API error"
**Solution:** 
- Verify API key is set: `echo $OPENAI_API_KEY`
- Check API key has sufficient credits
- Ensure you're using the correct model name

### Issue: "Java compiler not found"
**Solution:** Install JDK (not just JRE):
```bash
sudo dnf install java-21-openjdk-devel
javac -version  # Verify installation
```

## Performance Considerations

- **Async Processing**: Document processing happens asynchronously to avoid blocking API requests
- **Chunking Strategy**: Smart sentence-boundary splitting preserves context
- **Vector Search**: ChromaDB provides fast similarity search even with large document collections
- **Token Management**: Top-K limits prevent exceeding GPT-4 context windows

## Security Notes

⚠️ **Important Security Considerations:**

1. **Authentication**: This version does not include authentication. Add Spring Security before production deployment.
2. **API Keys**: Never commit API keys to version control. Use environment variables.
3. **Input Validation**: File size and format validation is implemented, but consider additional security measures for production.
4. **Rate Limiting**: Consider adding rate limiting for production use.

## Future Enhancements

- [ ] Add user authentication and authorization
- [ ] Implement conversation history/memory
- [ ] Support for multiple file formats (DOCX, TXT, etc.)
- [ ] Streaming responses via Server-Sent Events (SSE)
- [ ] Document comparison features
- [ ] Automatic document summarization
- [ ] Redis caching for frequently asked questions
- [ ] Kubernetes deployment manifests

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
- [ChromaDB](https://www.trychroma.com/) for vector database
- [Apache PDFBox](https://pdfbox.apache.org/) for PDF processing
