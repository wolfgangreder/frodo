#!/bin/bash
#
# Frodo Firebird Docker Setup Script
#
# This script starts a Firebird 5.0 container and creates the database
# with UTF-8 character set and 32K page size.
#
# Prerequisites:
# - Docker installed and running
# - Docker Compose installed (optional)
#

set -e

echo "=== Frodo Firebird Docker Setup ==="
echo

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo "ERROR: Docker is not running. Please start Docker and try again."
  exit 1
fi

# Method 1: Docker Compose (recommended)
if command -v docker-compose > /dev/null 2>&1; then
  echo "Using Docker Compose..."
  echo
  
  # Start Firebird container
  echo "Starting Firebird container..."
  docker-compose up -d firebird
  
  # Wait for container to be ready
  echo "Waiting for Firebird to start (30 seconds)..."
  sleep 30
  
  # Check if container is running
  if ! docker-compose ps | grep -q "frodo-firebird.*Up"; then
    echo "ERROR: Firebird container failed to start. Check logs with: docker-compose logs firebird"
    exit 1
  fi
  
  echo "Firebird container is running."
  echo
  
  # Create database with UTF-8 and 32K page size
  echo "Creating database with UTF-8 charset and 32K page size..."
  docker-compose exec -T firebird isql -user sysdba -password masterkey << 'EOF'
CREATE DATABASE '/firebird/data/frodo.fdb'
  PAGE_SIZE 32768
  DEFAULT CHARACTER SET UTF8
  USER 'sysdba'
  PASSWORD 'masterkey';
QUIT;
EOF
  
  echo
  echo "Verifying database configuration..."
  docker-compose exec -T firebird isql -user sysdba -password masterkey /firebird/data/frodo.fdb -q << 'EOF'
SELECT 
  MON$PAGE_SIZE AS PAGE_SIZE, 
  RDB$CHARACTER_SET_NAME AS DEFAULT_CHARSET
FROM MON$DATABASE 
CROSS JOIN RDB$DATABASE;
EOF
  
  echo
  echo "✓ Success! Firebird database created with UTF-8 and 32K page size."
  echo
  echo "Connection details:"
  echo "  Host: localhost"
  echo "  Port: 3050"
  echo "  Database: /firebird/data/frodo.fdb"
  echo "  Username: sysdba"
  echo "  Password: masterkey"
  echo
  echo "Update application.properties with:"
  echo "  quarkus.datasource.jdbc.url=jdbc:firebirdsql://localhost:3050//firebird/data/frodo.fdb?encoding=UTF8&charSet=utf-8"
  echo
  echo "Start Frodo application with: ./gradlew quarkusDev"
  
else
  # Method 2: Docker run (fallback)
  echo "Docker Compose not found. Using docker run..."
  echo
  
  # Check if container already exists
  if docker ps -a | grep -q frodo-firebird; then
    echo "Container 'frodo-firebird' already exists."
    echo "Starting existing container..."
    docker start frodo-firebird
  else
    echo "Creating and starting Firebird container..."
    docker run -d \
      --name frodo-firebird \
      -e ISC_PASSWORD=masterkey \
      -p 3050:3050 \
      -v frodo-firebird-data:/firebird/data \
      firebirdsql/firebird:5.0
  fi
  
  # Wait for container to be ready
  echo "Waiting for Firebird to start (30 seconds)..."
  sleep 30
  
  # Create database
  echo "Creating database with UTF-8 charset and 32K page size..."
  docker exec -i frodo-firebird isql -user sysdba -password masterkey << 'EOF'
CREATE DATABASE '/firebird/data/frodo.fdb'
  PAGE_SIZE 32768
  DEFAULT CHARACTER SET UTF8
  USER 'sysdba'
  PASSWORD 'masterkey';
QUIT;
EOF
  
  echo
  echo "Verifying database configuration..."
  docker exec -i frodo-firebird isql -user sysdba -password masterkey /firebird/data/frodo.fdb -q << 'EOF'
SELECT 
  MON$PAGE_SIZE AS PAGE_SIZE, 
  RDB$CHARACTER_SET_NAME AS DEFAULT_CHARSET
FROM MON$DATABASE 
CROSS JOIN RDB$DATABASE;
EOF
  
  echo
  echo "✓ Success! Firebird database created with UTF-8 and 32K page size."
  echo
  echo "Connection details:"
  echo "  Host: localhost"
  echo "  Port: 3050"
  echo "  Database: /firebird/data/frodo.fdb"
  echo "  Username: sysdba"
  echo "  Password: masterkey"
  echo
  echo "Update application.properties with:"
  echo "  quarkus.datasource.jdbc.url=jdbc:firebirdsql://localhost:3050//firebird/data/frodo.fdb?encoding=UTF8&charSet=utf-8"
  echo
  echo "Start Frodo application with: ./gradlew quarkusDev"
fi

echo
echo "=== Setup Complete ==="
