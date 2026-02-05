# Project Context: TrendStream

## 1. Tech Stack
- Java 21, Spring Boot 3.x, Gradle
- MySQL 8.0 (Port 3307), JPA (Hibernate 6)
- Kafka, Zookeeper, Kafka UI
- Google Gemini API (`gemini-2.5-flash`)
- Swagger (SpringDoc OpenAPI)
- Library: `io.hypersistence:hypersistence-utils-hibernate-63`

## 2. Coding Conventions & Critical Rules (DO NOT CHANGE)

### 2.1 Entity & JSON Mapping (Strict)
- **DO NOT** use `AttributeConverter` for JSON columns.
- **MUST USE** `io.hypersistence.utils.hibernate.type.json.JsonType` with `@Type(JsonType.class)`.
- The `News` entity constructor must be annotated with `@Builder` and include ALL fields (`title`, `link`, `description`, `source`, `type`, `pubDate`, `aiResult`).
- The `News` entity has a One-to-Many relationship with the `NewsTag` entity.

### 2.2 Network Configuration
- Application Server Port: `8081` (Strictly enforced to avoid 8080).
- Server Address: `0.0.0.0`
- Kafka Bootstrap: `localhost:9092` (Host), `kafka:29092` (Docker Internal).

### 2.3 Kafka & Date Handling
- **Producer**: Uses `JsonSerializer`.
- **Consumer**: Uses `JsonDeserializer` with trusted packages `*`.
- **Consumer 역할**: DB 저장만 담당 (AI 분석은 스케줄러가 배치 처리)
- **Date Parsing**: Naver API provides date strings (e.g., "Sun, ..."). Consumer MUST parse this into `LocalDateTime` using `DateTimeFormatter` with `Locale.ENGLISH`.

### 2.4 JPQL & Pageable 규칙 (Important)
- **JPQL 쿼리에서 ORDER BY 사용 금지** (Pageable sort와 충돌 발생)
- 정렬은 Controller의 `@PageableDefault`에서 지정
- Native Query는 예외: 쿼리 내에서 직접 ORDER BY 지정 필요

### 2.5 AI 분석 배치 처리 (Important)
- **배치 크기**: 3개씩 묶어서 1회 API 호출
- **스케줄러**: `NewsAnalysisScheduler`가 30초마다 실행
- **Rate Limit 관리**: 스케줄러가 단독으로 담당 (KafkaConfig에서는 제어하지 않음)
- **처리 흐름**: Consumer → DB 저장 (aiResult=null) → 스케줄러 → 배치 분석 → 업데이트

### 2.6 Architecture Flow
```
[Step 1] NaverNewsProducer → Kafka (topic: dev-news)
[Step 2] NewsConsumer → 중복 체크 → DB 저장 (aiResult = null)
[Step 3] NewsAnalysisScheduler → 5개씩 배치 조회
[Step 4] GeminiService.analyzeBatchNews() → 배치 AI 분석
[Step 5] 분석 결과 DB 업데이트
[Step 6] NewsController → REST API로 데이터 제공
```

## 3. Project Structure
```
src/main/java/com/example/trendstream/
├── TrendStreamApplication.java
├── config/
│   └── KafkaConfig.java
├── controller/
│   └── NewsController.java
├── domain/
│   ├── entity/
│   │   ├── News.java
│   │   ├── NewsTag.java
│   │   └── Tag.java
│   ├── enums/
│   │   └── NewsType.java
│   └── vo/
│       └── AiResponse.java
├── dto/
│   ├── GeminiInterfaceDto.java
│   ├── NaverApiDto.java
│   ├── NewsMessage.java
│   └── NewsResponseDto.java
├── repository/
│   ├── NewsRepository.java
│   └── NewsTagRepository.java
└── service/
    ├── GeminiService.java              # analyzeBatchNews() 배치 분석
    ├── NaverNewsProducer.java
    ├── NewsConsumer.java               # DB 저장만 담당
    ├── NewsAnalysisScheduler.java      # 배치 분석 스케줄러 (신규)
    └── NewsService.java
```

## 4. Implemented Features

### 4.1 Data Pipeline (Completed)
- **Producer**: 5분마다 Naver API에서 뉴스 수집, 중복 방지 캐시 적용
- **Consumer**: 날짜 파싱, 중복 체크, DB 저장 (AI 분석 분리)
- **AI 배치 분석**: 30초마다 5개씩 묶어서 Gemini API 호출 (80% 절약)

### 4.2 REST API (Completed)
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/news` | GET | 최신순 뉴스 목록 (페이지네이션) |
| `/api/news/{id}` | GET | 뉴스 상세 조회 |
| `/api/news/search?keyword=xxx` | GET | 키워드 검색 (제목, 설명) |
| `/api/news/popular` | GET | AI 중요도 점수순 정렬 |

**Swagger UI**: `http://localhost:8081/swagger-ui.html`

### 4.3 Repository Methods
- `existsByLink()`: 중복 체크
- `findByIdWithTags()`: Fetch Join으로 상세 조회
- `findAllByOrderByPubDateDesc()`: 최신순 페이지네이션
- `searchByKeyword()`: 키워드 검색
- `findAllByOrderByScoreDesc()`: JSON 필드 기반 정렬 (Native Query)
- `findByAiResultIsNull()`: AI 분석 대기 뉴스 조회 (배치용)

## 5. Infrastructure
| Service | Port | Description |
|---------|------|-------------|
| Spring Boot | 8081 | Application Server |
| MySQL | 3307 | Database |
| Kafka | 9092 | Message Queue |
| Kafka UI | 8080 | Kafka 모니터링 |
| Zookeeper | 2181 | Kafka 코디네이터 |

## 6. Current Status
- ✅ 인프라 구축 완료 (Docker)
- ✅ 데이터 파이프라인 완료 (Naver → Kafka → DB)
- ✅ AI 배치 분석 완료 (5개씩 묶어서 처리)
- ✅ REST API 개발 완료 (NewsController)
- ✅ Swagger 문서화 완료

## 7. 향후 개발 방향 (Roadmap)

### 1단계: 핵심 기능 완성 ✅ (Completed)
- ~~REST API 개발 (`NewsController`)~~
- ~~AI 배치 처리 도입~~

### 2단계: 서비스 가치 증대 (Current)
- **프론트엔드 개발**: Next.js로 뉴스 목록 UI 개발 (별도 저장소)
- **Ollama 하이브리드**: 로컬 LLM 도입으로 API 한도 완전 해결 (계획 중)
- **실시간 트렌드 분석**: 키워드 집계 → 트렌드 순위 표시
- **검색 기능 고도화**: AI 요약 검색, Elasticsearch 도입 검토

### 3단계: 개인화 및 확장 (Long-term)
- **사용자 시스템**: 회원가입/로그인, 키워드 구독
- **알림 기능**: 이메일/웹 푸시 알림
- **데이터 소스 확장**: 해외 IT 블로그, RSS 피드 추가

## 8. 관련 문서
- `TROUBLESHOOTING.md`: 트러블슈팅 기록 & 향후 개선점
- `FRONTEND_CLAUDE.md`: 프론트엔드 개발용 API 스펙 (별도 저장소용)
