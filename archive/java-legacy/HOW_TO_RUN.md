# How to Run the Application

## Prerequisites

- **Java 17 or higher** (check with: `java -version`)
- **Maven 3.6 or higher** (check with: `mvn -version`)

## Quick Start

### Method 1: Using the Startup Script (Easiest)

```bash
# Make the script executable (first time only)
chmod +x start.sh

# Run the application
./start.sh
```

This script will:
1. Check if Java and Maven are installed
2. Build the project
3. Start the Spring Boot application

### Method 2: Using Maven Directly

```bash
# Navigate to project directory
cd /Users/swapnilbobade1/Documents/Trading

# Build and run in one command
mvn spring-boot:run
```

### Method 3: Build JAR and Run

```bash
# Build the project
mvn clean package

# Run the JAR file
java -jar target/multibagger-swing-trading-1.0.0.jar
```

## Application URLs

Once the application starts, you'll see:
```
Started MultibaggerSwingTradingApplication in X.XXX seconds
```

The application will be available at:

- **Base URL**: `http://localhost:8080`
- **Comprehensive Strategy API**: `http://localhost:8080/api/comprehensive-strategy`
- **H2 Database Console**: `http://localhost:8080/h2-console`

## Test the New Comprehensive Strategy Endpoints

### 1. Health Check
```bash
curl http://localhost:8080/api/comprehensive-strategy/health
```

### 2. Analyze Buy Decision
```bash
curl http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE
```

### 3. Backtest Strategy (with 100,000 capital)
```bash
curl "http://localhost:8080/api/comprehensive-strategy/backtest/RELIANCE?capital=100000"
```

### 4. Backtest with Custom Dates
```bash
curl "http://localhost:8080/api/comprehensive-strategy/backtest/TCS?capital=100000&startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59"
```

## Troubleshooting

### Port Already in Use
If port 8080 is already in use, you can change it by adding to `application.yml`:
```yaml
server:
  port: 8081
```

### Build Errors
```bash
# Clean and rebuild
mvn clean install

# Skip tests if needed
mvn clean install -DskipTests
```

### Java Version Issues
Make sure you're using Java 17:
```bash
# Check Java version
java -version

# Should show: openjdk version "17" or higher
```

### Maven Not Found
Install Maven:
```bash
# macOS (using Homebrew)
brew install maven

# Linux (Ubuntu/Debian)
sudo apt-get install maven

# Or download from: https://maven.apache.org/download.cgi
```

## Running in Background

### Using nohup
```bash
nohup mvn spring-boot:run > app.log 2>&1 &
```

### Using screen
```bash
screen -S trading-app
mvn spring-boot:run
# Press Ctrl+A then D to detach
# Reattach with: screen -r trading-app
```

### Using tmux
```bash
tmux new -s trading-app
mvn spring-boot:run
# Press Ctrl+B then D to detach
# Reattach with: tmux attach -t trading-app
```

## Stop the Application

- **If running in foreground**: Press `Ctrl+C`
- **If running in background**: Find the process and kill it:
  ```bash
  # Find the process
  ps aux | grep java
  
  # Kill the process (replace PID with actual process ID)
  kill -9 <PID>
  
  # Or kill by port
  lsof -ti:8080 | xargs kill -9
  ```

## Development Mode (Auto-reload)

For development with auto-reload, add Spring Boot DevTools:
```bash
# Already included in pom.xml, just run:
mvn spring-boot:run
```

Changes will automatically reload (may need IDE configuration).

## Production Deployment

For production, build and run the JAR:
```bash
# Build
mvn clean package -DskipTests

# Run with production profile
java -jar target/multibagger-swing-trading-1.0.0.jar --spring.profiles.active=prod
```

## Logs

Logs are configured in `application.yml`. To see detailed logs:
```bash
# View logs in real-time
tail -f app.log

# Or if running with Maven
mvn spring-boot:run | tee app.log
```

## Quick Test Commands

```bash
# Test all endpoints
echo "Testing Health Check..."
curl http://localhost:8080/api/comprehensive-strategy/health

echo -e "\n\nTesting Buy Analysis..."
curl http://localhost:8080/api/comprehensive-strategy/analyze/RELIANCE | jq .

echo -e "\n\nTesting Backtesting..."
curl "http://localhost:8080/api/comprehensive-strategy/backtest/RELIANCE?capital=100000" | jq .
```

Note: `jq` is optional for pretty JSON formatting. Install with: `brew install jq` (macOS) or `sudo apt-get install jq` (Linux)

