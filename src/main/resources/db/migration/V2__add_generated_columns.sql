-- ============================================
-- TrendStream 쿼리 최적화 마이그레이션
-- Version: V2
-- Date: 2026-02-08
-- Description: JSON 필드에 대한 Generated Column + Index 추가
-- ============================================

-- ============================================
-- 배경 (왜 필요한가?)
-- ============================================
--
-- [문제 상황]
-- 1. findAllByOrderByScoreDesc(): 매번 JSON_EXTRACT(ai_result, '$.score')로 파싱 후 정렬
--    → 데이터 10,000건 기준 O(n log n) 시간 복잡도 + JSON 파싱 오버헤드
--
-- 2. findByAiResultIsNull(): ai_result IS NULL 조건
--    → NULL 체크에 인덱스 활용 불가 → Full Table Scan
--
-- 3. findByAiResultFailed(): ai_result ->> '$.summary' = '분석 실패' 조건
--    → 매번 JSON 파싱 + 문자열 비교 → Full Table Scan
--
-- [해결 방법]
-- MySQL 5.7+의 Generated Column (가상 컬럼) 사용
-- - JSON 필드에서 값을 추출하여 별도 컬럼에 저장
-- - 해당 컬럼에 인덱스 생성
-- - 쿼리 시 JSON 파싱 없이 인덱스 스캔으로 조회
--
-- [Generated Column 종류]
-- - VIRTUAL: 조회할 때마다 계산 (저장 공간 절약, CPU 사용)
-- - STORED: INSERT/UPDATE 시 계산하여 저장 (저장 공간 사용, 조회 빠름)
-- → 인덱스가 필요하므로 STORED 사용
-- ============================================


-- ============================================
-- 1. AI 점수 가상 컬럼 (ai_score)
-- ============================================
--
-- [대상 쿼리]
-- findAllByOrderByScoreDesc()
--
-- [현재 쿼리]
-- SELECT * FROM news
-- WHERE ai_result IS NOT NULL
-- ORDER BY JSON_EXTRACT(ai_result, '$.score') DESC
--
-- [문제점]
-- - ORDER BY에서 매번 JSON_EXTRACT() 호출
-- - 인덱스 사용 불가 → filesort 발생
-- - 데이터 증가 시 정렬 시간 급증
--
-- [해결 후 쿼리]
-- SELECT * FROM news
-- WHERE ai_score IS NOT NULL
-- ORDER BY ai_score DESC
--
-- [기대 효과]
-- - JSON 파싱 제거
-- - idx_ai_score 인덱스로 정렬 (이미 정렬된 상태)
-- - O(n log n) → O(log n)
-- ============================================

ALTER TABLE news ADD COLUMN ai_score INT
GENERATED ALWAYS AS (JSON_EXTRACT(ai_result, '$.score')) STORED;

CREATE INDEX idx_ai_score ON news(ai_score DESC);


-- ============================================
-- 2. 분석 완료 여부 가상 컬럼 (is_analyzed)
-- ============================================
--
-- [대상 쿼리]
-- findByAiResultIsNull()
--
-- [현재 쿼리]
-- SELECT * FROM news WHERE ai_result IS NULL LIMIT ?
--
-- [문제점]
-- - ai_result 컬럼은 JSON 타입 (평균 500+ bytes)
-- - NULL 체크에도 전체 컬럼 스캔 필요
-- - ai_result 컬럼에 인덱스를 걸기 어려움 (JSON 타입)
--
-- [해결 후 쿼리]
-- SELECT * FROM news WHERE is_analyzed = 0 LIMIT ?
--
-- [기대 효과]
-- - TINYINT(1) 컬럼으로 인덱스 스캔
-- - Full Table Scan → Index Scan
-- - NewsAnalysisScheduler가 10초마다 호출하므로 효과 큼
-- ============================================

ALTER TABLE news ADD COLUMN is_analyzed TINYINT(1)
GENERATED ALWAYS AS (CASE WHEN ai_result IS NULL THEN 0 ELSE 1 END) STORED;

CREATE INDEX idx_is_analyzed ON news(is_analyzed);


-- ============================================
-- 3. AI 요약 가상 컬럼 (ai_summary)
-- ============================================
--
-- [대상 쿼리]
-- findByAiResultFailed()
--
-- [현재 쿼리]
-- SELECT * FROM news WHERE ai_result ->> '$.summary' = '분석 실패'
--
-- [문제점]
-- - 매번 JSON에서 summary 추출 (->> 연산)
-- - 문자열 비교 수행
-- - 인덱스 사용 불가 → Full Table Scan
--
-- [해결 후 쿼리]
-- SELECT * FROM news WHERE ai_summary = '분석 실패' LIMIT ?
--
-- [기대 효과]
-- - JSON 파싱 제거
-- - VARCHAR 인덱스로 빠른 조회
-- - 분석 실패 뉴스 재처리 속도 향상
--
-- [주의사항]
-- - ai_result ->> '$.summary'는 따옴표 없는 문자열 반환
-- - VARCHAR(500)은 요약 최대 길이 고려
-- - 인덱스는 첫 100자만 사용 (충분함)
-- ============================================

ALTER TABLE news ADD COLUMN ai_summary VARCHAR(500)
GENERATED ALWAYS AS (ai_result ->> '$.summary') STORED;

CREATE INDEX idx_ai_summary ON news(ai_summary(100));


-- ============================================
-- 검증 쿼리 (마이그레이션 후 실행)
-- ============================================
--
-- 1. 컬럼 생성 확인
-- DESCRIBE news;
--
-- 2. 인덱스 생성 확인
-- SHOW INDEX FROM news;
--
-- 3. 데이터 확인
-- SELECT id, ai_score, is_analyzed, LEFT(ai_summary, 50) FROM news LIMIT 10;
--
-- 4. 쿼리 실행 계획 확인 (인덱스 사용 여부)
-- EXPLAIN SELECT * FROM news WHERE ai_score IS NOT NULL ORDER BY ai_score DESC LIMIT 10;
-- EXPLAIN SELECT * FROM news WHERE is_analyzed = 0 LIMIT 10;
-- EXPLAIN SELECT * FROM news WHERE ai_summary = '분석 실패' LIMIT 10;
-- ============================================
