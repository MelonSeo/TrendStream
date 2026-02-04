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

## 향후 개선점 (Improvements)

### 1. Ollama 로컬 LLM 하이브리드 (예정)
**우선순위**: 높음 (API 한도 완전 해결)
**상태**: 계획 중

**현재 상태**:
- Gemini API 무료 티어 사용 중
- 배치 처리로 80% 절약했지만 여전히 한도 존재

**개선 방안**: Ollama + Gemini 하이브리드
```
[일반 뉴스] → Ollama (로컬, 무제한)
[중요 뉴스] → Gemini API (고품질)
```

**Ollama 설정**:
```bash
# 설치
curl -fsSL https://ollama.com/install.sh | sh

# 모델 다운로드
ollama pull llama3.1      # 8GB VRAM
ollama pull mistral       # 8GB VRAM
ollama pull phi3:mini     # 4GB VRAM (가벼움)

# API 엔드포인트
http://localhost:11434/api/generate
```

**구현 계획**:
1. `OllamaService` 생성 (GeminiService와 동일 인터페이스)
2. `AiAnalyzer` 인터페이스 추출
3. 설정으로 Ollama/Gemini 선택 가능하게
4. 또는 점수 기반 자동 선택

**기대 효과**:
- API 비용: 0원 (로컬 처리)
- 분석 속도: 약간 느림 (허용 범위)
- 데이터 프라이버시: 외부 전송 없음

---

### 2. JSON 필드 인덱싱 최적화
**우선순위**: 중간 (데이터 증가 시 필요)

**현재 상태**:
- `ai_result` JSON 컬럼의 `score` 값으로 정렬 시 Full Table Scan 발생
- 데이터가 적을 때는 문제없지만, 수만 건 이상이면 성능 저하

**현재 쿼리**:
```sql
SELECT * FROM news
WHERE ai_result IS NOT NULL
ORDER BY JSON_EXTRACT(ai_result, '$.score') DESC
```

**개선 방안**:
```sql
-- 1. 가상 컬럼(Generated Column) 생성
ALTER TABLE news
ADD COLUMN ai_score INT
GENERATED ALWAYS AS (JSON_EXTRACT(ai_result, '$.score')) STORED;

-- 2. 인덱스 추가
CREATE INDEX idx_ai_score ON news(ai_score DESC);
```

**기대 효과**:
- JSON 파싱 없이 인덱스로 직접 정렬 가능
- O(n log n) → O(log n) 성능 개선

**구현 시점**: 뉴스 데이터가 10,000건 이상 축적되었을 때

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

## 해결된 이슈 히스토리

| 날짜 | 이슈 | 해결 방법 |
|-----|-----|---------|
| 2026-02-03 | JPQL ORDER BY + Pageable 충돌 | 쿼리에서 ORDER BY 제거, Pageable에 위임 |
| 2026-02-03 | Gemini API 한도 빠른 소진 | 배치 처리 도입 (5개씩 묶어서 호출) |

---

*이 문서는 프로젝트 진행 중 발생한 문제와 개선점을 기록합니다.*
