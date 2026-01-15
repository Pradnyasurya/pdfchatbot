#!/bin/bash
# PDF Chatbot Startup Script
# Starts database containers and the Spring Boot application

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

echo "========================================="
echo "  PDF Chatbot - Startup Script"
echo "========================================="

# Check if .env file exists, if not create from example
if [ ! -f "$ENV_FILE" ]; then
    if [ -f "$SCRIPT_DIR/.env.example" ]; then
        echo "Creating .env file from .env.example..."
        cp "$SCRIPT_DIR/.env.example" "$ENV_FILE"
        echo ""
        echo "ERROR: Please edit .env file and set your OPENAI_API_KEY"
        echo "  File location: $ENV_FILE"
        echo ""
        exit 1
    else
        echo "ERROR: .env file not found and no .env.example to copy from"
        exit 1
    fi
fi

# Load .env file
echo "Loading environment from .env file..."
set -a
source "$ENV_FILE"
set +a

# Validate OPENAI_API_KEY
if [ -z "$OPENAI_API_KEY" ] || [ "$OPENAI_API_KEY" = "your-openai-api-key-here" ]; then
    echo ""
    echo "ERROR: OPENAI_API_KEY is not set or still has placeholder value"
    echo "Please edit .env file and set your OpenAI API key"
    echo "  File location: $ENV_FILE"
    echo ""
    exit 1
fi

echo "OpenAI API Key: ${OPENAI_API_KEY:0:10}..."

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed or not in PATH"
    echo "Please install Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# Check if docker-compose is available
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
elif docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    echo "ERROR: docker-compose is not available"
    echo "Please install Docker Compose: https://docs.docker.com/compose/install/"
    exit 1
fi

# Start PostgreSQL container
echo ""
echo "Starting PostgreSQL with PgVector..."
$DOCKER_COMPOSE up -d postgres

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
RETRIES=30
until docker exec pdf-chatbot-postgres pg_isready -U chatbot -d pdfchatbot > /dev/null 2>&1; do
    RETRIES=$((RETRIES - 1))
    if [ $RETRIES -le 0 ]; then
        echo "ERROR: PostgreSQL failed to start within timeout"
        exit 1
    fi
    echo "  Waiting for database... ($RETRIES attempts remaining)"
    sleep 2
done
echo "PostgreSQL is ready!"

# Create uploads directory if it doesn't exist
mkdir -p "$SCRIPT_DIR/uploads"

# Start the Spring Boot application
echo ""
echo "Starting PDF Chatbot application..."
echo "========================================="
echo ""

cd "$SCRIPT_DIR"
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2g -Xms1g"
