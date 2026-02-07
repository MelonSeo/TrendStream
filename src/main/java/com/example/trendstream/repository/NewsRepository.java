package com.example.trendstream.repository;

import com.example.trendstream.domain.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 뉴스 Repository (데이터 접근 계층)
 *
 * [JpaRepository 상속 시 제공되는 기본 메서드]
 * - save(entity): 저장/수정
 * - findById(id): ID로 조회
 * - findAll(): 전체 조회
 * - delete(entity): 삭제
 * - count(): 개수 조회
 *
 * [쿼리 메서드 작성 규칙]
 * - findBy + 필드명: 해당 필드로 조회
 * - existsBy + 필드명: 존재 여부 확인
 * - countBy + 필드명: 개수 조회
 * - 여러 조건: And, Or로 연결 (findByTitleAndSource)
 */
public interface NewsRepository extends JpaRepository<News, Long> {

    /**
     * 링크 중복 확인 (뉴스 수집 시 중복 방지용)
     *
     * [사용처]
     * - NewsConsumer에서 Kafka 메시지 수신 시 중복 체크
     * - 이미 DB에 있는 링크면 처리 건너뜀
     *
     * [생성되는 SQL]
     * SELECT EXISTS(SELECT 1 FROM news WHERE link = ?)
     *
     * @param link 확인할 뉴스 URL
     * @return 존재하면 true, 없으면 false
     */
    boolean existsByLink(String link);

    /**
     * 링크로 뉴스 조회
     *
     * [사용처]
     * - 기존 뉴스 업데이트 시 조회용
     *
     * @param link 조회할 뉴스 URL
     * @return 뉴스 엔티티 (없으면 null)
     */
    News findByLink(String link);

    /**
     * 전체 뉴스 + 태그 조회 (Fetch Join)
     *
     * [🔥 N+1 문제 해결 핵심]
     * - 일반 findAll() 사용 시: 뉴스 10개 조회 → 태그 조회 쿼리 10번 추가 = 총 11번
     * - JOIN FETCH 사용 시: 뉴스 + 태그 한 번에 조회 = 총 1번
     *
     * [DISTINCT 사용 이유]
     * - JOIN으로 인한 중복 행 제거
     * - 뉴스 1개에 태그 3개면 3행이 되는데, DISTINCT로 1개로 합침
     *
     * [LEFT JOIN FETCH 설명]
     * - LEFT: 태그 없는 뉴스도 조회 (INNER면 태그 없는 뉴스 제외됨)
     * - FETCH: 연관 엔티티를 영속성 컨텍스트에 즉시 로딩
     *
     * @return 태그 정보가 함께 로딩된 뉴스 목록 (최신순)
     */
    @Query("SELECT DISTINCT n FROM News n LEFT JOIN FETCH n.newsTags nt LEFT JOIN FETCH nt.tag ORDER BY n.pubDate DESC")
    List<News> findAllWithTags();

    /**
     * ID로 뉴스 + 태그 조회 (Fetch Join)
     *
     * [사용처]
     * - 뉴스 상세 조회 API (GET /api/news/{id})
     * - 한 번의 쿼리로 뉴스와 태그 모두 로딩
     *
     * [@Param 어노테이션]
     * - JPQL의 :id와 메서드 파라미터 id를 바인딩
     * - 파라미터 이름이 같으면 생략 가능하지만 명시하는 게 안전
     *
     * @param id 조회할 뉴스 ID
     * @return Optional로 감싼 뉴스 (없으면 Optional.empty())
     */
    @Query("SELECT n FROM News n LEFT JOIN FETCH n.newsTags nt LEFT JOIN FETCH nt.tag WHERE n.id = :id")
    Optional<News> findByIdWithTags(@Param("id") Long id);

    /**
     * 최신순 뉴스 목록 (페이지네이션)
     *
     * [페이지네이션 동작]
     * - Pageable에서 page, size 정보 추출
     * - LIMIT, OFFSET 절 자동 생성
     * - 예: page=1, size=10 → LIMIT 10 OFFSET 10
     *
     * [정렬 처리]
     * - ORDER BY를 쿼리에서 제거하고 Pageable에 위임
     * - Controller의 @PageableDefault에서 기본 정렬 지정 (pubDate DESC)
     * - 이렇게 하면 Pageable sort와 충돌 없음
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 페이지 객체 (content, totalElements, totalPages 등 포함)
     */
    Page<News> findAllByOrderByPubDateDesc(Pageable pageable);

    /**
     * 키워드 검색 (제목 + 설명 + AI 요약)
     *
     * [검색 대상]
     * - title: 뉴스 제목
     * - description: 뉴스 설명
     * - ai_result.summary: AI가 생성한 요약
     *
     * [Native Query 사용 이유]
     * - JSON 내부 필드(summary) 접근 필요 → JSON_EXTRACT 사용
     *
     * @param keyword 검색할 키워드
     * @param pageable 페이지 정보
     * @return 검색 결과
     */
    @Query(value = "SELECT * FROM news WHERE title LIKE CONCAT('%', :keyword, '%') " +
            "OR description LIKE CONCAT('%', :keyword, '%') " +
            "OR (ai_result IS NOT NULL AND ai_result ->> '$.summary' LIKE CONCAT('%', :keyword, '%')) " +
            "ORDER BY pub_date DESC",
            countQuery = "SELECT COUNT(*) FROM news WHERE title LIKE CONCAT('%', :keyword, '%') " +
                    "OR description LIKE CONCAT('%', :keyword, '%') " +
                    "OR (ai_result IS NOT NULL AND ai_result ->> '$.summary' LIKE CONCAT('%', :keyword, '%'))",
            nativeQuery = true)
    Page<News> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * AI 분석이 안 된 뉴스 조회 (배치 처리용)
     *
     * [사용처]
     * - NewsAnalysisScheduler에서 배치 분석할 뉴스 조회
     * - aiResult가 NULL인 뉴스만 가져옴
     *
     * @param pageable 페이지 정보 (size로 배치 크기 제어)
     * @return AI 분석이 필요한 뉴스 목록
     */
    Page<News> findByAiResultIsNull(Pageable pageable);

    /**
     * AI 분석 실패 뉴스 조회 (재분석용)
     *
     * [사용처]
     * - NewsAnalysisScheduler에서 분석 실패한 뉴스 재처리
     * - aiResult의 summary가 '분석 실패'인 뉴스 조회
     *
     * [Native Query 사용 이유]
     * - JSON 내부 필드(summary) 접근 필요 → JSON_EXTRACT 사용
     *
     * @param pageable 페이지 정보 (size로 배치 크기 제어)
     * @return 분석 실패한 뉴스 목록
     */
    @Query(value = "SELECT * FROM news WHERE ai_result ->> '$.summary' = '분석 실패'",
            countQuery = "SELECT COUNT(*) FROM news WHERE ai_result ->> '$.summary' = '분석 실패'",
            nativeQuery = true)
    Page<News> findByAiResultFailed(Pageable pageable);

    /**
     * AI 중요도 점수순 정렬 (Native Query)
     *
     * [Native Query 사용 이유]
     * - JPQL은 JSON 필드 내부 값 접근을 지원하지 않음
     * - MySQL의 JSON_EXTRACT() 함수로 aiResult JSON에서 score 추출
     *
     * [JSON_EXTRACT 문법]
     * - JSON_EXTRACT(column, '$.key'): JSON에서 특정 키의 값 추출
     * - aiResult가 {"score": 85}면 → 85 반환
     *
     * [countQuery 필요 이유]
     * - 페이지네이션 시 전체 개수 조회용 쿼리 필요
     * - Native Query는 자동 생성 안 되므로 직접 지정
     *
     * [WHERE 조건]
     * - aiResult가 NULL인 뉴스는 제외 (AI 분석 안 된 뉴스)
     *
     * @param pageable 페이지 정보
     * @return 중요도 점수 내림차순 정렬된 뉴스 목록
     */
    @Query(value = "SELECT * FROM news WHERE ai_result IS NOT NULL ORDER BY JSON_EXTRACT(ai_result, '$.score') DESC",
            countQuery = "SELECT COUNT(*) FROM news WHERE ai_result IS NOT NULL",
            nativeQuery = true)
    Page<News> findAllByOrderByScoreDesc(Pageable pageable);

    /**
     * 태그(키워드) 기반 검색
     *
     * [장점]
     * - 정확한 태그 매칭 (인덱스 사용 가능)
     * - AI가 추출한 키워드로 검색
     *
     * @param tagName 검색할 태그 이름
     * @param pageable 페이지 정보
     * @return 해당 태그가 있는 뉴스 목록
     */
    @Query(value = "SELECT DISTINCT n.* FROM news n " +
            "JOIN news_tags nt ON n.id = nt.news_id " +
            "JOIN tags t ON nt.tag_id = t.id " +
            "WHERE t.name = :tagName " +
            "ORDER BY n.pub_date DESC",
            countQuery = "SELECT COUNT(DISTINCT n.id) FROM news n " +
                    "JOIN news_tags nt ON n.id = nt.news_id " +
                    "JOIN tags t ON nt.tag_id = t.id " +
                    "WHERE t.name = :tagName",
            nativeQuery = true)
    Page<News> findByTagName(@Param("tagName") String tagName, Pageable pageable);

    /**
     * 카테고리(검색 키워드)별 뉴스 조회
     *
     * [사용처]
     * - 특정 카테고리의 뉴스 목록 조회 (GET /api/news/category?name=백엔드)
     *
     * @param searchKeyword 검색 키워드 (카테고리)
     * @param pageable 페이지 정보
     * @return 해당 카테고리의 뉴스 목록
     */
    @Query(value = "SELECT * FROM news WHERE search_keyword = :searchKeyword ORDER BY pub_date DESC",
            countQuery = "SELECT COUNT(*) FROM news WHERE search_keyword = :searchKeyword",
            nativeQuery = true)
    Page<News> findBySearchKeyword(@Param("searchKeyword") String searchKeyword, Pageable pageable);

    /**
     * 사용 가능한 카테고리(검색 키워드) 목록 조회
     *
     * [사용처]
     * - 카테고리 목록 API (GET /api/news/categories)
     *
     * @return 중복 제거된 검색 키워드 목록
     */
    @Query(value = "SELECT DISTINCT search_keyword FROM news WHERE search_keyword IS NOT NULL ORDER BY search_keyword",
            nativeQuery = true)
    List<String> findDistinctSearchKeywords();
}
