# Trendyol Scraper

A Java Playwright application to search for products on Trendyol and retrieve product names based on specific rankings. It supports true parallelism and an emergency stop feature.

## Requirements
- Java 11 or higher
- Maven
- Internet connection

## Usage

Compile the project:
```bash
mvn clean compile
```

Run the application:
```bash
mvn exec:java -Dexec.mainClass="com.trendyolscraper.MainController" -Dexec.args="<numberOfWorkers> <mode> <gpuEnabled>"
```

### Arguments
- `<numberOfWorkers>`: Number of concurrent browser instances (e.g., 5).
- `<mode>`: `headless` (background) or `headed` (visible browser).
- `<gpuEnabled>`: `true` or `false`. `true` attempts to use the GPU (useful for macOS).

### Examples
Run 3 workers in headless mode without GPU:
```bash
mvn exec:java -Dexec.mainClass="com.trendyolscraper.MainController" -Dexec.args="3 headless false"
```

Run 2 workers in headed mode with GPU enabled:
```bash
mvn exec:java -Dexec.mainClass="com.trendyolscraper.MainController" -Dexec.args="2 headed true"
```

## Emergency Stop
While the application is running, you can press the `q` key followed by `Enter` in the console to trigger an emergency stop. This will forcefully shut down all running browser instances.