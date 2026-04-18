#!/bin/bash

echo "🚀 Starting Multibagger Swing Trading Strategy System"
echo "=================================================="

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 17 or higher."
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed. Please install Maven 3.6 or higher."
    exit 1
fi

echo "✅ Java and Maven are installed"

# Build the project
echo "🔨 Building the project..."
mvn clean install -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please check the errors above."
    exit 1
fi

echo "✅ Build successful"

# Start the application
echo "🚀 Starting the application..."
echo "📊 API will be available at: http://localhost:8080/api/trading"
echo "🗄️  H2 Database Console: http://localhost:8080/h2-console"
echo "🧪 Test Endpoints: http://localhost:8080/api/test"
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

mvn spring-boot:run
