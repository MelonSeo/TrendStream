# TrendStream 트러블슈팅 & 개선점 기록

## 트러블슈팅 (Troubleshooting)

### 1. JPQL ORDER BY + Pageable Sort 충돌 문제
**날짜**: 2026-02-03

**증상**:
```
org.hibernate.query.sqm.PathElementException: Could not resolve attribute 'string' of 'com.example.trendstream.domain.entity.News'
[SELECT n FROM News n WHERE n.title LIKE :keyword ... ORDER BY n.pubDate DESC, n.string asc]
```

**원인**:
- JPQL 쿼리에 `ORDER BY n.pubDate DESC`가 이미 있음
- Pageable의 sort 파라미터가 추가로 붙으면서 `n.string asc` 같은 잘못된 정렬 조건 추가
- Spring Data JPA는 쿼리의 ORDER BY와 Pageable sort를 병합함

**해결 방법**:
1. JPQL 쿼리에서 `ORDER BY` 제거
2. Controller의 `@PageableDefault`에서 기본 정렬 지정
3. 정렬은 Pageable에 완전히 위임

**수정 파일**:
- `NewsRepository.java`: 쿼리에서 ORDER BY 제거
- `NewsController.java`: `@PageableDefault(sort = "pubDate", direction = Sort.Direction.DESC)` 추가

**교훈**:
> JPQL과 Pageable을 함께 사용할 때, 정렬은 한 곳에서만 담당하도록 설계해야 한다.
> Native Query는 Pageable sort가 적용되지 않으므로 쿼리 내에서 직접 정렬 지정 필요.

---

### 2. Gemini API 무료 한도 빠른 소진 문제
**날짜**: 2026-02-03

**증상**:
- 뉴스 1개당 API 1회 호출 → 무료 한도(분당 15회) 빠르게 소진
- Rate Limit 에러 발생

**원인**:
- Consumer가 Kafka 메시지 수신 즉시 개별 API 호출
- 비효율적인 API 사용 패턴

**해결 방법**: 배치 처리 도입
1. Consumer는 DB 저장만 담당 (AI 분석 제거)
2. 별도 스케줄러(`NewsAnalysisScheduler`)가 30초마다 실행
3. 5개씩 묶어서 1회 API 호출 (80% 절약)

**수정/생성 파일**:
- `NewsConsumer.java`: AI 분석 로직 제거, DB 저장만
- `GeminiService.java`: `analyzeBatchNews()` 배치 분석 메서드 추가
- `NewsAnalysisScheduler.java`: 배치 분석 스케줄러 (신규)
- `NewsRepository.java`: `findByAiResultIsNull()` 메서드 추가

**개선 효과**:
| 항목 | AS-IS | TO-BE |
|-----|-------|-------|
| 뉴스 10개 처리 | API 10회 | API 2회 |
| API 사용량 | 100% | 20% |

---

### 3. 네이버 API 응답 인코딩 문제 (HTML 엔티티 + UTF-8 깨짐)
**날짜**: 2026-02-06

**증상**:
- 제목에 `&quot;`, `&amp;` 등 HTML 엔티티가 그대로 노출
- 일부 뉴스 제목이 `ë��ì�¸ ë�¹ì�...`처럼 깨져서 저장됨

**원인**:
1. **HTML 엔티티**: 네이버 API가 HTML 인코딩된 문자열을 반환. `replaceAll("<[^>]*>", "")`로 태그만 제거하고 엔티티(`&quot;` 등)는 디코딩하지 않음
2. **UTF-8 깨짐**: `MappingJackson2HttpMessageConverter`가 응답 헤더의 charset을 따르면서 UTF-8 한글이 깨짐. `StringHttpMessageConverter`만 UTF-8 설정하고 Jackson 컨버터는 누락

**해결 방법**:
1. `decodeHtml()` 메서드 추가 — `&quot;`, `&amp;`, `&lt;`, `&gt;`, `&apos;`, `&#39;` 디코딩
2. `MappingJackson2HttpMessageConverter`에도 `setDefaultCharset(UTF_8)` 강제 지정

**수정 파일**:
- `NaverNewsProducer.java`: `decodeHtml()` 추가, Jackson 컨버터 UTF-8 설정

**주의사항**:
> 수정 이전에 DB에 저장된 깨진 데이터는 자동으로 복구되지 않음. 필요 시 수동 정리 또는 재수집 필요.

---

## 개발 기록 (Development Log)

### 1. Ollama 로컬 LLM 하이브리드 도입
**날짜**: 2026-02-06

**배경**:
- Gemini API 무료 티어 한도 제한 (분당 15회)
- 배치 처리로 80% 절약했지만 여전히 한도 존재
- 로컬 LLM 도입으로 API 의존도 완전 제거 필요

**설계 — Strategy 패턴 기반 하이브리드 구조**:
```
application.properties: ai.provider=ollama (or gemini)
                              │
NewsAnalysisScheduler ──→ AiAnalyzer (인터페이스)
                              ├── OllamaService (implements) — 로컬 LLM
                              └── GeminiService (implements) — 외부 API
```

**구현 내용**:

| 파일 | 변경 | 설명 |
|-----|------|------|
| `service/AiAnalyzer.java` | 신규 | `analyzeBatchNews()` 공통 인터페이스 |
| `service/OllamaService.java` | 신규 | Ollama REST API 호출 (`POST /api/generate`) |
| `config/AiConfig.java` | 신규 | `ai.provider` 값에 따라 빈 선택 |
| `service/GeminiService.java` | 수정 | `implements AiAnalyzer` 추가 |
| `service/NewsAnalysisScheduler.java` | 수정 | `GeminiService` → `AiAnalyzer` 의존 변경 |
| `application.properties` | 수정 | `ai.provider`, `ollama.api.url`, `ollama.model` 추가 |

**핵심 설계 결정**:
1. **Strategy 패턴**: `AiAnalyzer` 인터페이스로 추상화하여 런타임에 구현체 교체 가능
2. **프롬프트 재사용**: GeminiService와 OllamaService가 동일한 프롬프트 사용 → 분석 결과 일관성 유지
3. **설정 기반 전환**: `ai.provider` 프로퍼티 한 줄로 ollama/gemini 전환 (재배포 없이 변경 가능)
4. **기존 코드 영향 최소화**: 스케줄러만 의존 타입 변경, 나머지 로직 그대로 유지

**전환 방법**:
```properties
# Ollama 사용 (로컬, 무제한)
ai.provider=ollama
ollama.model=gemma3:4b

# Gemini 사용 (외부 API)
ai.provider=gemini
```

**사전 작업 (Ollama)**:
```bash
# 1. Ollama 설치: https://ollama.com/download
# 2. 모델 다운로드
ollama pull gemma3:4b
# 3. 서버 실행 확인
ollama list
```

**효과**:
| 항목 | Gemini (기존) | Ollama (신규) |
|-----|-------------|--------------|
| API 비용 | 무료 티어 한도 있음 | 0원 (로컬) |
| Rate Limit | 분당 15회 | 무제한 |
| 데이터 프라이버시 | 외부 전송 | 로컬 처리 |
| 분석 품질 | 높음 | 모델에 따라 다름 |

---

## 향후 개선점 (Improvements)

### ~~1. Ollama 로컬 LLM 하이브리드~~ ✅ 완료 (2026-02-06)
> 개발 기록 섹션 참고

---

### 2. JSON 필드 인덱싱 최적화 (Generated Column 활용)
**우선순위**: 중간 (데이터 10,000건 이상 시 적용)

**대상 쿼리 3개**:
| 쿼리 | 현재 문제 | 최적화 방안 |
|-----|----------|------------|
| `findAllByOrderByScoreDesc()` | JSON 파싱 후 정렬 | ai_score 가상 컬럼 |
| `findByAiResultIsNull()` | ai_result NULL 체크 | is_analyzed 가상 컬럼 |
| `findByAiResultFailed()` | JSON 내부 문자열 비교 | ai_summary 가상 컬럼 |

**적용할 SQL 마이그레이션**:
```sql
-- ============================================
-- TrendStream JSON 필드 최적화 마이그레이션
-- 적용 시점: 데이터 10,000건 이상 축적 시
-- 실행 전 백업 권장
-- ============================================

-- 1. AI 점수 가상 컬럼 (인기순 정렬 최적화)
-- 영향: findAllByOrderByScoreDesc() → O(n log n) → O(log n)
ALTER TABLE news ADD COLUMN ai_score INT
GENERATED ALWAYS AS (JSON_EXTRACT(ai_result, '$.score')) STORED;
CREATE INDEX idx_ai_score ON news(ai_score DESC);

-- 2. 분석 완료 여부 가상 컬럼 (미분석 뉴스 조회 최적화)
-- 영향: findByAiResultIsNull() → Full Table Scan → Index Scan
ALTER TABLE news ADD COLUMN is_analyzed TINYINT(1)
GENERATED ALWAYS AS (CASE WHEN ai_result IS NULL THEN 0 ELSE 1 END) STORED;
CREATE INDEX idx_is_analyzed ON news(is_analyzed);

-- 3. AI 요약 가상 컬럼 (분석 실패 조회 최적화)
-- 영향: findByAiResultFailed() → JSON 파싱 제거
ALTER TABLE news ADD COLUMN ai_summary VARCHAR(500)
GENERATED ALWAYS AS (ai_result ->> '$.summary') STORED;
CREATE INDEX idx_ai_summary ON news(ai_summary(100));
```

**마이그레이션 후 Repository 쿼리 변경**:
```java
// findAllByOrderByScoreDesc
SELECT * FROM news WHERE ai_score IS NOT NULL ORDER BY ai_score DESC

// findByAiResultIsNull
SELECT * FROM news WHERE is_analyzed = 0 LIMIT ?

// findByAiResultFailed
SELECT * FROM news WHERE ai_summary = '분석 실패' LIMIT ?
```

**기대 효과**:
- JSON 파싱 오버헤드 제거
- 인덱스 스캔으로 O(log n) 조회
- 스케줄러 쿼리 성능 대폭 개선 (10초마다 실행되므로 중요)

---

### 3. 키워드 검색 성능 개선
**우선순위**: 낮음 (장기 과제)

**현재 상태**:
- `LIKE '%keyword%'` 패턴으로 Full Table Scan
- 제목(title)과 설명(description)만 검색
- AI 요약(summary)은 검색 대상 아님

**개선 방안**:
1. **단기**: AI 요약(summary) 필드도 검색 대상에 추가
2. **중기**: MySQL Full-Text Index 도입
3. **장기**: Elasticsearch 도입으로 전문 검색 엔진 활용

**Elasticsearch 도입 시 장점**:
- 형태소 분석으로 한국어 검색 최적화
- 유사어, 오타 교정 검색
- 검색 결과 랭킹 알고리즘 적용

---

## SQL 쿼리 정리 (면접 대비)

### 1. `existsByLink(String link)` — 중복 체크
**쿼리 메서드** (Spring Data JPA 자동 생성)
```sql
SELECT EXISTS(SELECT 1 FROM news WHERE link = ?)
```
**사용 이유**: Kafka Consumer에서 뉴스 저장 전 중복 방지. `SELECT *` 대신 `EXISTS`를 쓰면 일치하는 행을 찾는 즉시 탐색을 멈추므로 효율적이다.

**인덱스 활용**: `link` 컬럼에 `UNIQUE` 제약조건이 걸려 있어 자동으로 유니크 인덱스 생성 → 인덱스 스캔으로 O(log n) 조회.

---

### 2. `findAllWithTags()` — N+1 문제 해결 (FETCH JOIN)
**JPQL**
```sql
SELECT DISTINCT n FROM News n
  LEFT JOIN FETCH n.newsTags nt
  LEFT JOIN FETCH nt.tag
ORDER BY n.pubDate DESC
```
**사용 이유**: 뉴스 목록 + 태그를 **1번의 쿼리**로 조회. 일반 `findAll()`은 N+1 문제 발생 (뉴스 10개 → 태그 조회 10번 추가 = 11번).

**핵심 키워드**:
| 키워드 | 설명 |
|-------|------|
| `LEFT JOIN FETCH` | 태그 없는 뉴스도 포함 + 연관 엔티티 즉시 로딩 (INNER면 태그 없는 뉴스 누락) |
| `DISTINCT` | JOIN으로 인한 중복 행 제거. 뉴스 1개 + 태그 3개 → 3행이 되는 걸 1행으로 합침 |
| `FETCH` | 일반 JOIN은 SELECT 절에만 반영. FETCH는 영속성 컨텍스트에 로딩하여 LazyInitializationException 방지 |

**면접 포인트**: "N+1 문제가 뭔가요?" → 연관 엔티티를 Lazy Loading으로 조회할 때, 부모 N개에 대해 자식을 각각 1번씩 추가 쿼리하는 문제. FETCH JOIN으로 해결.

**최적화 가능**: 현재는 전체 뉴스를 가져오므로 Pageable 미지원. 데이터 증가 시 `@EntityGraph` + Pageable 조합으로 변경 검토. (FETCH JOIN + Pageable은 메모리에서 페이징하는 문제 있음 → HHH000104 경고)

---

### 3. `findByIdWithTags(Long id)` — 상세 조회 (FETCH JOIN)
**JPQL**
```sql
SELECT n FROM News n
  LEFT JOIN FETCH n.newsTags nt
  LEFT JOIN FETCH nt.tag
WHERE n.id = :id
```
**사용 이유**: 뉴스 상세 조회 시 태그까지 1번에 가져오기. PK 조건이므로 `DISTINCT` 불필요.

**인덱스 활용**: `WHERE n.id = :id`는 PK 인덱스 사용 → O(log n).

---

### 4. `searchByKeyword(String keyword, Pageable pageable)` — 키워드 검색
**JPQL**
```sql
SELECT n FROM News n
WHERE n.title LIKE %:keyword% OR n.description LIKE %:keyword%
```
**사용 이유**: 제목 또는 설명에 키워드가 포함된 뉴스 검색.

**성능 이슈**: `%keyword%` (앞뒤 와일드카드)는 **인덱스 사용 불가 → Full Table Scan**.
- B-Tree 인덱스는 접두사 매칭(`keyword%`)만 가능
- 양쪽 `%`면 모든 행을 순차 탐색

**최적화 방안**:
1. **MySQL Full-Text Index**: `MATCH(title, description) AGAINST(:keyword IN BOOLEAN MODE)` → 역인덱스 기반 검색
2. **Elasticsearch 도입**: 형태소 분석, 유사어 검색, 랭킹 알고리즘 지원
3. **Generated Column**: 자주 검색하는 패턴이면 가상 컬럼 + 인덱스 조합

---

### 5. `findByAiResultIsNull(Pageable pageable)` — 미분석 뉴스 조회
**쿼리 메서드** (자동 생성)
```sql
SELECT * FROM news WHERE ai_result IS NULL LIMIT ? OFFSET ?
```
**사용 이유**: 스케줄러에서 AI 분석 안 된 뉴스를 배치로 가져오기. Pageable로 BATCH_SIZE 제어.

**인덱스 고려**: `ai_result IS NULL` 조건에 인덱스가 없어 Full Table Scan. 현재 데이터량에서는 문제없지만, 대부분의 뉴스가 분석 완료된 상태에서 소수의 NULL을 찾는 경우 부분 인덱스가 유용할 수 있음. (MySQL은 부분 인덱스 미지원 → Generated Column으로 우회 가능)

---

### 6. `findByAiResultFailed(Pageable pageable)` — 분석 실패 뉴스 재조회
**Native Query**
```sql
SELECT * FROM news WHERE ai_result ->> '$.summary' = '분석 실패'
```
**사용 이유**: JSON 내부 필드 값으로 필터링. JPQL은 JSON 함수 미지원이라 Native Query 필수.

**`->>` vs `JSON_EXTRACT()` 차이 (면접 빈출)**:
| 연산자 | 반환 | 예시 결과 |
|-------|------|----------|
| `JSON_EXTRACT(col, '$.key')` | JSON 타입 (따옴표 포함) | `"분석 실패"` |
| `col ->> '$.key'` | 문자열 타입 (따옴표 제거) | `분석 실패` |

`->>`는 `JSON_UNQUOTE(JSON_EXTRACT(...))`의 축약. 문자열 비교 시 반드시 `->>` 사용해야 정상 매칭.

**최적화 가능**: 자주 조회한다면 Generated Column 도입:
```sql
ALTER TABLE news ADD COLUMN ai_summary VARCHAR(255)
  GENERATED ALWAYS AS (ai_result ->> '$.summary') STORED;
CREATE INDEX idx_ai_summary ON news(ai_summary);
```

---

### 7. `findAllByOrderByScoreDesc(Pageable pageable)` — AI 점수순 정렬
**Native Query**
```sql
SELECT * FROM news
WHERE ai_result IS NOT NULL
ORDER BY JSON_EXTRACT(ai_result, '$.score') DESC
```
**사용 이유**: JSON 내부 `score` 값 기준 정렬. JPQL은 JSON 함수 미지원.

**countQuery 별도 지정 이유**: Native Query + Pageable 조합에서 Spring Data JPA는 `COUNT(*)` 쿼리를 자동 생성 못함. `countQuery`를 명시하지 않으면 파싱 에러 발생.

**성능 이슈**: `ORDER BY JSON_EXTRACT(...)` → 매 정렬 시 JSON 파싱 필요 → 데이터 증가 시 느려짐.

**최적화 방안** (Generated Column + 인덱스):
```sql
ALTER TABLE news ADD COLUMN ai_score INT
  GENERATED ALWAYS AS (JSON_EXTRACT(ai_result, '$.score')) STORED;
CREATE INDEX idx_ai_score ON news(ai_score DESC);

-- 최적화 후 쿼리
SELECT * FROM news WHERE ai_score IS NOT NULL ORDER BY ai_score DESC;
```
→ JSON 파싱 없이 인덱스 스캔으로 정렬. O(n log n) → O(log n).

---

### 8. `findTopTrendingSince(LocalDateTime since, int limit)` — 트렌드 집계
**Native Query**
```sql
SELECT t.name AS tag_name, COUNT(nt.id) AS cnt
FROM news_tags nt
  JOIN tags t ON nt.tag_id = t.id
  JOIN news n ON nt.news_id = n.id
WHERE n.pub_date >= :since
GROUP BY t.name
ORDER BY cnt DESC
LIMIT :limit
```
**사용 이유**: 기간 내 키워드 빈도를 집계하여 트렌드 순위 산출. 3-테이블 JOIN + GROUP BY + COUNT 필요.

**실행 순서** (면접 빈출: SQL 실행 순서):
```
FROM → JOIN → WHERE → GROUP BY → SELECT(COUNT) → ORDER BY → LIMIT
```

**인덱스 활용**:
- `news.pub_date`에 인덱스 있음 (`idx_pub_date`) → WHERE 범위 검색 최적화
- `news_tags.tag_id`, `news_tags.news_id`는 FK로 인덱스 자동 생성 → JOIN 최적화

**최적화 가능**: 트렌드 조회가 빈번하면 **Materialized View** 또는 별도 집계 테이블을 두고 스케줄러로 주기적 갱신하는 CQRS 패턴 검토.

---

### 9. `findRecentNewsByTagName(...)` — 키워드별 관련 뉴스
**Native Query**
```sql
SELECT n.id, n.title, n.link
FROM news_tags nt
  JOIN tags t ON nt.tag_id = t.id
  JOIN news n ON nt.news_id = n.id
WHERE t.name = :tagName AND n.pub_date >= :since
ORDER BY n.pub_date DESC
LIMIT :limit
```
**사용 이유**: 특정 트렌드 키워드를 클릭하면 관련 뉴스 목록 제공. 필요한 컬럼(id, title, link)만 SELECT하여 네트워크 비용 절감.

**SELECT 절 최적화 포인트**: `SELECT *` 대신 필요한 3개 컬럼만 조회 → 불필요한 `description(TEXT)`, `ai_result(JSON)` 전송 안 함.

---

### 쿼리 유형 요약 (JPQL vs Native Query vs 쿼리 메서드)

| 유형 | 사용 조건 | 프로젝트 예시 |
|-----|----------|-------------|
| **쿼리 메서드** | 단순 조건 (필드명 기반) | `existsByLink()`, `findByAiResultIsNull()` |
| **JPQL** | 엔티티 기반 쿼리, FETCH JOIN | `findAllWithTags()`, `searchByKeyword()` |
| **Native Query** | JSON 함수, GROUP BY 집계, DB 종속 기능 | `findAllByOrderByScoreDesc()`, `findTopTrendingSince()` |

**면접 팁**: "왜 Native Query를 썼나요?" → JPQL은 데이터베이스에 종속적이지 않은 객체 지향 쿼리라 `JSON_EXTRACT`, `GROUP BY + LIMIT` 같은 DB 종속 기능은 지원하지 않는다. 이런 경우에만 Native Query를 선택적으로 사용했다.

---

## 해결된 이슈 히스토리

| 날짜 | 이슈 | 해결 방법 |
|-----|-----|---------|
| 2026-02-03 | JPQL ORDER BY + Pageable 충돌 | 쿼리에서 ORDER BY 제거, Pageable에 위임 |
| 2026-02-03 | Gemini API 한도 빠른 소진 | 배치 처리 도입 (5개씩 묶어서 호출) |
| 2026-02-06 | 뉴스 제목 HTML 엔티티 노출 + UTF-8 깨짐 | decodeHtml() 추가, Jackson 컨버터 UTF-8 설정 |
| 2026-02-06 | Ollama 로컬 LLM 하이브리드 도입 | AiAnalyzer 인터페이스 + Strategy 패턴으로 ollama/gemini 전환 |
| 2026-02-07 | application.properties 한글 인코딩 깨짐 | 유니코드 이스케이프 변환 (`\uXXXX`) |
| 2026-02-07 | Native Query + Pageable sort 충돌 | PageRequest.of()로 sort 제외한 Pageable 생성 |
| 2026-02-07 | 카테고리별 뉴스 조회 기능 추가 | searchKeyword 필드 + API 엔드포인트 + 프론트엔드 페이지 |

---

## 개발 기록 - 2026-02-07

### 1. 실시간 트렌드 분석 기능 구현

**배경**: AI가 추출한 keywords를 활용하여 인기 키워드 순위 제공

**구현 내용**:

| 파일 | 액션 | 설명 |
|-----|------|------|
| `repository/TagRepository.java` | 신규 | `findByName()` - find-or-create 패턴 |
| `repository/NewsTagRepository.java` | 수정 | `findTopTrendingSince()`, `findRecentNewsByTagName()` Native Query 추가 |
| `service/NewsAnalysisScheduler.java` | 수정 | `saveKeywordsAsTags()` - AI 분석 후 키워드를 Tag/NewsTag 테이블에 저장 |
| `dto/TrendResponseDto.java` | 신규 | keyword, count, relatedNews 필드 |
| `service/TrendService.java` | 신규 | `getTopTrends(period, limit)` - 기간별 트렌드 집계 |
| `controller/TrendController.java` | 신규 | `GET /api/trends?period=24h&limit=10` |

**데이터 흐름**:
```
AI 분석 완료 → keywords 추출 → Tag/NewsTag 저장 (소문자 정규화)
                                      ↓
GET /api/trends → GROUP BY + COUNT → 트렌드 순위 반환
```

---

### 2. Groq Rate Limit 헤더 로깅 추가

**수정 파일**: `service/GroqService.java`

**변경 내용**:
- `restTemplate.postForObject()` → `restTemplate.exchange()`로 변경
- 응답 헤더에서 Rate Limit 정보 추출하여 로깅

**로그 예시**:
```
>>>> [Groq Rate Limit] 남은 요청: 29, 남은 토큰: 5765, 리셋까지: 1m30s
```

---

### 3. Naver API 수집량 증가

**수정 파일**: `service/NaverNewsProducer.java`

**변경**: `queryParam("display", 10)` → `queryParam("display", 50)`

**효과**: 키워드당 10개 → 50개 (5개 키워드 × 50개 = 최대 250개/회)

---

### 4. application.properties 한글 인코딩 문제 해결

**증상**: 키워드 검색이 동작하지 않음

**원인**: `.properties` 파일은 기본적으로 ISO-8859-1로 읽힘 → UTF-8 한글 깨짐

**해결**:
```properties
# Before (깨짐)
naver.api.keywords=백엔드,AI,클라우드,자바,여기어때

# After (유니코드 이스케이프)
naver.api.keywords=\uBC31\uC5D4\uB4DC,AI,\uD074\uB77C\uC6B0\uB4DC,\uC790\uBC14,\uC5EC\uAE30\uC5B4\uB54C
```

**IntelliJ 설정** (한글 직접 입력 가능하게):
- Settings → Editor → File Encodings
- Default encoding for properties files: `UTF-8`
- ✅ Transparent native-to-ascii conversion

---

### 5. 프론트엔드 - 분석 중 상태 표시

**수정 파일**: `components/NewsCard.tsx`

**변경 내용**: `aiResult`가 null일 때 표시
- 점수 뱃지 → "분석 중" (애니메이션 점)
- 요약 → "AI가 뉴스를 분석하고 있습니다..." (이탤릭)
- 키워드 → 스켈레톤 로딩 바

---

### 6. 검색 기능 고도화

#### 6.1 AI 요약도 검색 대상에 추가

**수정 파일**: `repository/NewsRepository.java`

**변경**:
```sql
-- Before
WHERE title LIKE '%keyword%' OR description LIKE '%keyword%'

-- After (Native Query)
WHERE title LIKE CONCAT('%', :keyword, '%')
   OR description LIKE CONCAT('%', :keyword, '%')
   OR (ai_result IS NOT NULL AND ai_result ->> '$.summary' LIKE CONCAT('%', :keyword, '%'))
```

#### 6.2 태그 기반 검색 API 추가

| 파일 | 변경 |
|-----|------|
| `NewsRepository.java` | `findByTagName()` Native Query 추가 |
| `NewsService.java` | `searchByTag()` 메서드 추가 |
| `NewsController.java` | `GET /api/news/tag?name=xxx` 엔드포인트 추가 |

#### 6.3 프론트엔드 태그 검색 페이지

| 파일 | 변경 |
|-----|------|
| `app/api.ts` | `searchByTag()` 함수 추가 |
| `app/news/tag/page.tsx` | 태그 검색 결과 페이지 (신규) |
| `components/NewsCard.tsx` | 키워드 클릭 → `/news/tag?name=xxx`로 이동 |

---

### 7. Native Query + Pageable Sort 충돌 해결

**증상**:
```
Unknown column 'n.pubDate' in 'order clause'
[... ORDER BY n.pub_date DESC, n.pubDate desc limit ?]
```

**원인**:
- Native Query: `ORDER BY n.pub_date DESC` (DB 컬럼명)
- Pageable: `ORDER BY n.pubDate DESC` (JPA 필드명) 추가됨
- MySQL은 `pubDate` 컬럼을 찾지 못함

**해결**:
```java
// NewsService.java
public Page<NewsResponseDto> searchByTag(String tagName, Pageable pageable) {
    // sort 없는 Pageable 생성
    Pageable unsortedPageable = PageRequest.of(
        pageable.getPageNumber(),
        pageable.getPageSize()
    );
    return newsRepository.findByTagName(tagName, unsortedPageable)
            .map(NewsResponseDto::from);
}
```

**교훈**:
> Native Query에서 ORDER BY를 직접 지정했다면, Service에서 Pageable의 sort를 제거해야 충돌 방지.

---

### 8. 카테고리(검색 키워드)별 뉴스 조회 기능

**배경**: Naver API 검색 시 사용하는 키워드(백엔드, AI, 클라우드 등)별로 뉴스를 그룹화하여 조회하는 기능 필요

**구현 내용**:

#### 백엔드 변경

| 파일 | 액션 | 설명 |
|-----|------|------|
| `domain/entity/News.java` | 수정 | `searchKeyword` 필드 추가, 인덱스 추가 (`idx_search_keyword`) |
| `dto/NewsMessage.java` | 수정 | `searchKeyword` 필드 추가 (Kafka 메시지) |
| `dto/NewsResponseDto.java` | 수정 | `searchKeyword` 필드 추가, `from()` 메서드 수정 |
| `service/NaverNewsProducer.java` | 수정 | NewsMessage에 검색 키워드 포함하여 전송 |
| `service/NewsConsumer.java` | 수정 | News 엔티티에 searchKeyword 저장 |
| `repository/NewsRepository.java` | 수정 | `findBySearchKeyword()`, `findDistinctSearchKeywords()` 추가 |
| `service/NewsService.java` | 수정 | `getNewsByCategory()`, `getCategories()` 추가 |
| `controller/NewsController.java` | 수정 | `GET /api/news/category`, `GET /api/news/categories` 추가 |

#### 프론트엔드 변경

| 파일 | 액션 | 설명 |
|-----|------|------|
| `app/api.ts` | 수정 | `getNewsByCategory()`, `getCategories()` 함수 추가 |
| `app/news/category/page.tsx` | 신규 | 카테고리 페이지 (카테고리 칩 버튼 + 뉴스 목록) |
| `app/layout.tsx` | 수정 | 네비게이션 + 푸터에 Category 링크 추가 |

#### API 스펙

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /api/news/category?name=AI` | AI 카테고리의 뉴스 목록 조회 |
| `GET /api/news/categories` | 사용 가능한 카테고리 목록 반환 `["AI", "백엔드", "클라우드", ...]` |

**주의사항**:
> 기존에 수집된 뉴스에는 `searchKeyword`가 null이므로, 카테고리 조회 시 표시되지 않음. 새로 수집되는 뉴스부터 카테고리 분류됨.

---

*이 문서는 프로젝트 진행 중 발생한 문제와 개선점을 기록합니다.*
