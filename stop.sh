#!/bin/bash
# PDF Chatbot Stop Script
# Stops the database containers

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Stopping PDF Chatbot containers..."

# Check which docker-compose command is available
if command -v docker-compose &> /dev/null; then
    docker-compose down
elif docker compose version &> /dev/null; then
    docker compose down
else
    echo "docker-compose not found, trying to stop container directly..."
    docker stop pdf-chatbot-postgres 2>/dev/null || true
fi

echo "Containers stopped."
