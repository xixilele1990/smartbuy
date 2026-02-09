# SmartBuy

SmartBuy is a backend service that combines:

- **BuyerProfile (persisted)**: user preferences stored in PostgreSQL (for future personalization/extension)
- **House + Scoring (no DB writes)**: pure DTOs and pure scoring logic (no house persistence required)
- **ATTOM integration**: fetches house details by address. plus schools and crime index data

## Prerequisites

- **Java 21**
- **PostgreSQL**

## Quick Start

1. **Create database**

```bash
createdb smartbuy
```

2. **Set environment variables**

```bash
# --- DB ---
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/smartbuy"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD=""

# --- CORS ---
# Comma-separated. Supports patterns like http://localhost:*
export APP_CORS_ALLOWED_ORIGINS="http://localhost:5173,https://smartbuy-frontend.onrender.com"

# --- ATTOM ---
export ATTOM_BASE_URL="https://api.gateway.attomdata.com"
export ATTOM_API_KEY="<<your-attom-api-key>>"
```

3. **Run**

```bash
./gradlew bootRun
```

Defaults:

- Connects to `localhost:5432/smartbuy` as user `postgres` (empty password by default)
- Uses `spring.jpa.hibernate.ddl-auto=update` to auto-create/update tables (e.g. `buyer_profiles`)

## API Reference (current endpoints)

All examples assume the service is running on `http://localhost:8080`.

### BuyerProfile (persisted)

#### POST `/buyerProfile` (create or update)

Request body:

```json
{
  "sessionId": "session-123",
  "maxPrice": 2000000,
  "minBedrooms": 3,
  "minBathrooms": 2.0,
  "priorityMode": "BALANCED"
}
```

```bash
curl -sS -X POST "http://localhost:8080/buyerProfile" \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"session-123","maxPrice":2000000,"minBedrooms":3,"minBathrooms":2.0,"priorityMode":"BALANCED"}'
```

#### GET `/buyerProfile/{sessionId}` (read)

```bash
curl -sS "http://localhost:8080/buyerProfile/session-123"
```

#### DELETE `/buyerProfile/{sessionId}` (delete)

```bash
curl -sS -X DELETE "http://localhost:8080/buyerProfile/session-123" -i
```

### Houses (no DB writes; ATTOM -> DTO + warnings)

#### POST `/api/houses/from-attom-hardcoded` (fetch a house by address)

```bash
curl -sS -X POST "http://localhost:8080/api/houses/from-attom-hardcoded?address1=2464%20Forbes%20Ave&address2=Santa%20Clara,%20CA%2095050"
```

Response:

- `house`: a House DTO (includes `avmValue`, `schoolsJson`, `crimeIndex`, etc.)
- `warnings`: soft warnings when ATTOM fields are missing (e.g. crime index unavailable)

#### POST `/api/houses/import` (batch fetch; max 5 addresses)

```bash
curl -sS -X POST "http://localhost:8080/api/houses/import" \
  -H "Content-Type: application/json" \
  -d '{"addresses":[{"address1":"2464 Forbes Ave","address2":"Santa Clara, CA 95050"},{"address1":"4529 Winona Court","address2":"Denver, CO"}]}'
```

### Scoring (no DB writes; total + dimension scores + summary)

#### POST `/api/score/house` (score a provided buyerProfile + house)

```bash
curl -sS -X POST "http://localhost:8080/api/score/house" \
  -H "Content-Type: application/json" \
  -d '{
    "buyerProfile":{"sessionId":"session-123","maxPrice":2000000,"minBedrooms":3,"minBathrooms":2.0,"priorityMode":"BALANCED"},
    "house":{"address1":"x","address2":"y","avmValue":1900000,"beds":3,"bathsTotal":2.0,"crimeIndex":33,"schoolsJson":"[{\"schoolRating\":\"A\"},{\"schoolRating\":\"B+\"}]"}
  }'
```

#### POST `/api/score/house-by-session` (load buyerProfile by sessionId, then score)

```bash
curl -sS -X POST "http://localhost:8080/api/score/house-by-session" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId":"session-123",
    "house":{"address1":"x","address2":"y","avmValue":1900000,"beds":3,"bathsTotal":2.0,"crimeIndex":33,"schoolsJson":"[{\"schoolRating\":\"A\"}]"}
  }'
```

#### POST `/api/score/batch-from-attom` (batch: addresses -> ATTOM -> score each; max 5)

Notes:

- `addresses`: max 5 (concurrent ATTOM fetch)
- Results are sorted by `totalScore` in **descending** order
- **No automatic de-duplication**: if the client sends duplicate addresses, you will get duplicate results. It's recommended to de-duplicate addresses on the frontend before submitting.

```bash
curl -sS -X POST "http://localhost:8080/api/score/batch-from-attom" \
  -H "Content-Type: application/json" \
  -d '{
    "buyerProfile":{"sessionId":"session-123","maxPrice":2000000,"minBedrooms":3,"minBathrooms":2.0,"priorityMode":"BALANCED"},
    "addresses":[
      {"address1":"2464 Forbes Ave","address2":"Santa Clara, CA 95050"},
      {"address1":"4529 Winona court","address2":"Denver"}
    ]
  }'
```

#### POST `/api/score/from-attom-hardcoded` (dev/debug endpoint)

Fetches a house from ATTOM by address and scores it using the BuyerProfile stored under a fixed sessionId (`"sessionId"`).

```bash
curl -sS -X POST "http://localhost:8080/api/score/from-attom-hardcoded?address1=4529%20Winona%20court&address2=Denver"
```

## Data constraints and recommendations

- **Address format**: ATTOM tends to match more reliably when `address2` is in the form `City, ST ZIP` (e.g. `Mountain View, CA 94043`).
- **Missing fields behavior**:
  - `HouseController` returns `warnings` when some ATTOM fields are missing
  - `ScoringService` is fail-fast for required fields (400), e.g. `house.avmValue`, `house.crimeIndex`, `house.schoolsJson`

## Error format

Most errors are returned in a consistent shape:

```json
{"error":"400 BAD_REQUEST","message":"house.crimeIndex is required"}
```

## Tests / Build

```bash
./gradlew test
./gradlew clean build
```
