# Project Context: TrendStream

## Tech Stack
- Java 21, Spring Boot 3.x, Gradle
- MySQL 8.0 (Port 3307), JPA (Hibernate 6)
- Kafka, Zookeeper, Kafka UI
- Google Gemini API
- Library: `io.hypersistence:hypersistence-utils-hibernate-63`

## Coding Conventions & Critical Rules (DO NOT CHANGE)
1. **Entity & JSON Mapping (Strict)**:
   - **DO NOT** use `AttributeConverter` for JSON columns.
   - **MUST USE** `io.hypersistence.utils.hibernate.type.json.JsonType` with `@Type(JsonType.class)`.
   - The `News` entity constructor must be annotated with `@Builder` and include ALL fields (`title`, `link`, `description`, `source`, `type`, `pubDate`, `aiResult`).
   - The `News` entity has a One-to-Many relationship with the `NewsTag` entity, which connects `News` to `Tag` entities.

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

## 2. Key Implementations (Completed)
- **Producer**: Collects news every 5 minutes. Implemented in-memory cache (`sentLinkCache`) to prevent duplicate Kafka messages.
- **Kafka Config (Critical)**:
   - Implemented **Rate Limiting** for Gemini Free Tier.
   - `max.poll.records = 1` (Process 1 message at a time).
   - `idleBetweenPolls = 15000` (Wait 15s between polls).
   - Configured via `KafkaConfig.java`, NOT `application.properties`.
- **Consumer**:
   - Parses `String` date to `LocalDateTime`.
   - Uses `gemini-2.5-flash` model.
   - Skips processing if `newsRepository.existsByLink()` is true.
- **Database**:
   - Table `news` with `JSON` column for AI results.
   - Uses `Hypersistence Utils` (`@Type(JsonType.class)`).
   - Entity uses `@Builder` covering all fields (`NewsType`, `AiResponse` included).
- **API-Rest**: `NewsController`가 구현되어 페이징, 검색, 인기순 조회 등 데이터 조회가 가능합니다.

## 3. Infrastructure
- **Ports**: Spring(`8081`), MySQL(`3307`), Kafka(`9092`), Kafka UI(`8080`).
- **Network**: All internal Docker communication issues resolved.

## Current Status
- Infrastructure is fully operational with correct Docker networking.
- Backend pipeline is verified: Data flows correctly from API to DB.
- Common errors (Bad address, Port conflict, JSON Mapping) are resolved.

## 4. 향후 개발 방향 (Roadmap)

### 1단계: 핵심 기능 완성 (Short-term)
- **REST API 개발 (`NewsController`)**: 데이터베이스에 저장된 뉴스 분석 데이터를 외부에서 사용할 수 있도록 API를 개발합니다.
  - **Endpoints 예시**:
    - `GET /api/news`: 최신순으로 뉴스 목록 조회
    - `GET /api/news/search?keyword={keyword}`: 특정 키워드로 뉴스 검색
    - `GET /api/news/popular`: AI가 분석한 '중요도 점수' 순으로 정렬하여 조회

### 2단계: 서비스 가치 증대 (Mid-term)
- **프론트엔드 개발**: React, Vue 등 최신 프레임워크를 사용하여 API를 호출하고, 분석된 뉴스 목록을 사용자에게 시각적으로 제공하는 웹 페이지를 개발합니다.(우선 간단한 UI로 시작)
- **실시간 트렌드 분석**: "TrendStream"이라는 이름에 걸맞게, 일정 시간 동안 가장 많이 등장한 키워드를 집계하여 '실시간 IT 트렌드' 순위를 보여주는 기능을 추가합니다.
- **검색 기능 고도화**: 단순 키워드 매칭을 넘어, AI가 생성한 '뉴스 요약' 내용까지 검색할 수 있도록 기능을 확장합니다. (필요 시 Elasticsearch 같은 전문 검색 엔진 도입 고려)

### 3단계: 개인화 및 확장 (Long-term)
- **사용자 시스템 도입**: 회원가입/로그인 기능을 추가하고, 사용자가 관심 키워드(예: "AI", "Apple")를 '구독'하면 관련 뉴스를 필터링하여 보여주는 개인화 기능을 구현합니다.
- **알림 기능**: 사용자가 구독한 키워드의 새로운 뉴스가 등록되면 이메일, 웹 푸시 등으로 알림을 보내주는 기능을 추가합니다.
- **데이터 소스 확장**: 네이버 뉴스 외에도 다양한 해외 IT 기술 블로그, 유명 개발자 트위터 등의 데이터를 RSS 피드나 API를 통해 수집하여 데이터의 폭과 깊이를 더합니다.