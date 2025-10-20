#!/bin/bash

# Dev Container Post-Create Script
# This script runs after the dev container is created

set -e  # Exit on any error

echo "🚀 Setting up Java development environment..."

# Update package lists
echo "📦 Updating package lists..."
sudo apt-get update

# Install Maven
echo "🔧 Installing Maven..."
sudo apt-get install -y maven

# Install Redis
echo "🔧 Installing Redis..."
sudo apt-get install -y redis-server

# Start Redis service
echo "🚀 Starting Redis service..."
sudo service redis-server start

# Verify Maven installation
echo "✅ Verifying Maven installation..."
mvn --version

# Verify Redis installation
echo "✅ Verifying Redis installation..."
redis-cli ping

# Clean and compile the project
echo "🏗️ Building the project..."
mvn clean compile

echo "🎉 Development environment setup complete!"
echo "📋 Available commands:"
echo "  - mvn clean compile    # Compile the project"
echo "  - mvn test             # Run tests"
echo "  - mvn exec:java         # Run the application"
echo "  - redis-cli ping        # Test Redis connection"
