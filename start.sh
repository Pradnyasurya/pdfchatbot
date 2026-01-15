#!/bin/bash
# PDF Chatbot Startup Script
# Loads environment variables from .env file and starts the application

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

# Load .env file if it exists
if [ -f "$ENV_FILE" ]; then
    echo "Loading environment from .env file..."
    set -a  # automatically export all variables
    source "$ENV_FILE"
    set +a
else
    echo "Warning: .env file not found at $ENV_FILE"
    echo "Create one from .env.example or set OPENAI_API_KEY environment variable"
fi

# Check if OPENAI_API_KEY is set
if [ -z "$OPENAI_API_KEY" ] || [ "$OPENAI_API_KEY" = "your-api-key-here" ]; then
    echo "Error: OPENAI_API_KEY is not set or still has placeholder value"
    echo "Please edit .env file and set your OpenAI API key"
    exit 1
fi

echo "Starting PDF Chatbot..."
echo "OpenAI API Key: ${OPENAI_API_KEY:0:10}***"

# Run the application with recommended JVM settings
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2g -Xms1g"
