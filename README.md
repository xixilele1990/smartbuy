# SmartBuy

## Prerequisites

- **Java 21**
- **PostgreSQL**

## Quick Start

1.  **Create Database**
    ```bash
    createdb smartbuy
    ```

2.  **Run Application**
    ```bash
    ./gradlew bootRun
    ```
    *Defaults: Connects to `localhost:5432/smartbuy` as user `postgres` (no password).*

## Configuration

Override defaults using environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/your_db
export SPRING_DATASOURCE_USERNAME=your_user


./gradlew bootRun


./gradlew test
```

## Build

```bash
./gradlew clean build
```
