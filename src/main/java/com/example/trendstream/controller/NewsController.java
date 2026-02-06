package com.example.trendstream.controller;

import com.example.trendstream.dto.NewsResponseDto;
import com.example.trendstream.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 뉴스 REST API 컨트롤러
 *
 * [REST API 설계 원칙]
 * 1. URI는 리소스를 표현 (명사 사용): /api/news
 * 2. HTTP 메서드로 행위 표현: GET(조회), POST(생성), PUT(수정), DELETE(삭제)
 * 3. 복수형 명사 사용: /news (단일 뉴스도 /news/{id}로 접근)
 *
 * [제공 API 목록]
 * - GET /api/news          : 최신순 뉴스 목록 조회
 * - GET /api/news/{id}     : 뉴스 상세 조회
 * - GET /api/news/search   : 키워드 검색
 * - GET /api/news/popular  : 인기 뉴스 (AI 점수순)
 *
 * [@RestController vs @Controller]
 * - @RestController = @Controller + @ResponseBody
 * - 모든 메서드 반환값이 자동으로 JSON 변환됨
 */
@Tag(name = "News API", description = "뉴스 데이터 조회 API")
@RestController
@RequestMapping("/api/news")  // 기본 경로 설정 (모든 메서드에 /api/news 접두사)
@RequiredArgsConstructor      // 생성자 주입을 위한 Lombok 어노테이션
public class NewsController {

    private final NewsService newsService;

    /**
     * 최신순 뉴스 목록 조회
     *
     * [API 스펙]
     * - URL: GET /api/news
     * - Query Params: page(기본0), size(기본10), sort(기본pubDate,DESC)
     *
     * [요청 예시]
     * - GET /api/news                    -> 첫 페이지, 10개
     * - GET /api/news?page=1&size=20     -> 2페이지, 20개
     * - GET /api/news?sort=title,asc     -> 제목 오름차순
     *
     * [@PageableDefault 역할]
     * - Pageable 파라미터의 기본값 설정
     * - 클라이언트가 값을 안 보내면 여기 설정된 값 사용
     *
     * @param pageable Spring Data가 자동으로 page, size, sort 파라미터를 바인딩
     * @return 페이지네이션된 뉴스 목록 (200 OK)
     */
    @Operation(summary = "최신 뉴스 목록 조회", description = "최신순으로 정렬된 뉴스 목록을 페이지네이션하여 조회합니다.")
    @GetMapping
    public ResponseEntity<Page<NewsResponseDto>> getLatestNews(
            @Parameter(hidden = true)
            @PageableDefault(size = 10, sort = "pubDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(newsService.getLatestNews(pageable));
    }

    /**
     * 뉴스 상세 조회
     *
     * [API 스펙]
     * - URL: GET /api/news/{id}
     * - Path Variable: id (뉴스 고유 식별자)
     *
     * [요청 예시]
     * - GET /api/news/1     -> ID가 1인 뉴스 조회
     * - GET /api/news/999   -> 없으면 예외 발생 (추후 ExceptionHandler로 처리)
     *
     * [@PathVariable 역할]
     * - URL 경로의 {id} 부분을 메서드 파라미터로 바인딩
     * - 타입 변환 자동 수행 (String -> Long)
     *
     * @param id 조회할 뉴스 ID
     * @return 뉴스 상세 정보 (200 OK)
     */
    @Operation(summary = "뉴스 상세 정보 조회", description = "뉴스 ID를 사용하여 특정 뉴스의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<NewsResponseDto> getNewsById(
            @Parameter(description = "조회할 뉴스의 ID", required = true, example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(newsService.getNewsById(id));
    }

    /**
     * 키워드로 뉴스 검색
     *
     * [API 스펙]
     * - URL: GET /api/news/search?keyword={keyword}
     * - Query Params: keyword(필수), page, size
     *
     * [요청 예시]
     * - GET /api/news/search?keyword=AI              -> "AI" 포함 뉴스 검색
     * - GET /api/news/search?keyword=Spring&page=2   -> 3페이지 결과
     *
     * [@RequestParam 역할]
     * - Query String 파라미터를 메서드 파라미터로 바인딩
     * - required=true가 기본값 (keyword 누락 시 400 Bad Request)
     *
     * @param keyword 검색 키워드 (제목, 설명에서 검색)
     * @param pageable 페이지 정보
     * @return 검색 결과 (200 OK)
     */
    @Operation(summary = "키워드 뉴스 검색", description = "제목 또는 요약 내용에 포함된 키워드로 뉴스를 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<Page<NewsResponseDto>> searchNews(
            @Parameter(description = "검색할 키워드", required = true, example = "AI")
            @RequestParam String keyword,
            @Parameter(hidden = true)
            @PageableDefault(size = 10, sort = "pubDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(newsService.searchNews(keyword, pageable));
    }

    /**
     * 인기 뉴스 조회 (AI 중요도 점수순)
     *
     * [API 스펙]
     * - URL: GET /api/news/popular
     * - Query Params: page, size
     *
     * [정렬 기준]
     * - AI가 분석한 중요도 점수(score) 내림차순
     * - aiResult가 없는 뉴스는 제외됨
     *
     * [요청 예시]
     * - GET /api/news/popular           -> 가장 중요한 뉴스 10개
     * - GET /api/news/popular?size=5    -> 상위 5개만
     *
     * @param pageable 페이지 정보
     * @return 중요도순 뉴스 목록 (200 OK)
     */
    @Operation(summary = "인기 뉴스 목록 조회", description = "AI가 분석한 중요도 점수(score)가 높은 순으로 뉴스 목록을 조회합니다.")
    @GetMapping("/popular")
    public ResponseEntity<Page<NewsResponseDto>> getPopularNews(
            @Parameter(hidden = true)
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(newsService.getPopularNews(pageable));
    }

    /**
     * 태그(키워드)로 뉴스 검색
     *
     * [API 스펙]
     * - URL: GET /api/news/tag?name={tagName}
     * - Query Params: name(필수), page, size
     *
     * [요청 예시]
     * - GET /api/news/tag?name=spring        -> "spring" 태그 뉴스
     * - GET /api/news/tag?name=ai&page=1     -> "ai" 태그 2페이지
     *
     * @param name 검색할 태그 이름
     * @param pageable 페이지 정보
     * @return 태그 검색 결과 (200 OK)
     */
    @Operation(summary = "태그 기반 뉴스 검색", description = "AI가 추출한 키워드(태그)로 뉴스를 검색합니다. 정확한 태그 매칭으로 빠른 검색이 가능합니다.")
    @GetMapping("/tag")
    public ResponseEntity<Page<NewsResponseDto>> searchByTag(
            @Parameter(description = "검색할 태그 이름", required = true, example = "spring")
            @RequestParam String name,
            @Parameter(hidden = true)
            @PageableDefault(size = 10) Pageable pageable) {
        // Native Query에서 ORDER BY 직접 지정 (pub_date DESC)
        return ResponseEntity.ok(newsService.searchByTag(name, pageable));
    }
}