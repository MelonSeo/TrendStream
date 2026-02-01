# Project Context: TrendStream

## Tech Stack
- Java 17, Spring Boot 3.x, Gradle
- MySQL 8.0 (Port 3307), JPA (Hibernate 6)
- Kafka, Zookeeper, Kafka UI
- Google Gemini API
- Library: `io.hypersistence:hypersistence-utils-hibernate-63`

## Coding Conventions & Critical Rules (DO NOT CHANGE)
1. **Entity & JSON Mapping (Strict)**:
    - **DO NOT** use `AttributeConverter` for JSON columns.
    - **MUST USE** `io.hypersistence.utils.hibernate.type.json.JsonType` with `@Type(JsonType.class)`.
    - The `News` entity constructor must be annotated with `@Builder` and include ALL fields (`title`, `link`, `description`, `source`, `type`, `pubDate`, `aiResult`).

2. **Network Configuration**:
    - Application Server Port: `8081` (Strictly enforced to avoid 8080).
    - Server Address: `0.0.0.0` (To prevent binding errors).
    - Kafka Bootstrap: `localhost:9092` (Host), `kafka:29092` (Docker Internal).

3. **Kafka & Date Handling**:
    - **Producer**: Uses `JsonSerializer`.
    - **Consumer**: Uses `JsonDeserializer` with trusted packages `*`.
    - **Date Parsing**: Naver API provides date strings (e.g., "Sun, ..."). Consumer MUST parse this into `LocalDateTime` using `DateTimeFormatter` with `Locale.ENGLISH`.

4. **Architecture Flow**:
    - **Step 1**: `NaverNewsProducer` collects data -> Sends `NewsMessage` (DTO) to Kafka.
    - **Step 2**: `NewsConsumer` receives message -> Checks duplication (`existsByLink`).
    - **Step 3**: `GeminiService` analyzes content -> Returns `AiResponse` (VO).
    - **Step 4**: `NewsConsumer` builds `News` entity (using correct Builder) -> Saves to MySQL.

## Current Status
- Infrastructure is fully operational with correct Docker networking.
- Backend pipeline is verified: Data flows correctly from API to DB.
- Common errors (Bad address, Port conflict, JSON Mapping) are resolved.