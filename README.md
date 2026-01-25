# SmartBuy

## Prerequisites

- **Java 21**

## Quick Start

1.  **Run Application**
    ```bash
    ./gradlew bootRun
    ```

## Configuration

### ATTOM API

This branch (`lexyno-db`) does **not** persist to a database. It fetches external data from ATTOM and
returns it from the backend for further scoring logic.

Configure ATTOM via environment variables (recommended):

```bash
export ATTOM_BASE_URL=https://api.gateway.attomdata.com
export ATTOM_API_KEY=your_key

./gradlew bootRun

./gradlew test
```

## Build

```bash
./gradlew clean build
```
