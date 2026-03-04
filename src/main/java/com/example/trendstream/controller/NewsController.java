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

    @Operation(summary = "최신 뉴스 목록 조회", description = "최신순으로 정렬된 뉴스 목록을 페이지네이션하여 조회합니다.")
    @GetMapping
    public ResponseEntity<Page<NewsResponseDto>> getLatestNews(
            @Parameter(hidden = true)
            @PageableDefault(size = 10, sort = "pubDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(newsService.getLatestNews(pageable));
    }


    @Operation(summary = "뉴스 상세 정보 조회", description = "뉴스 ID를 사용하여 특정 뉴스의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<NewsResponseDto> getNewsById(
            @Parameter(description = "조회할 뉴스의 ID", required = true, example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(newsService.getNewsById(id));
    }


    @Operation(summary = "키워드 뉴스 검색", description = "제목 또는 요약 내용에 포함된 키워드로 뉴스를 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<Page<NewsResponseDto>> searchNews(
            @Parameter(description = "검색할 키워드", required = true, example = "AI")
            @RequestParam String keyword,
            @Parameter(hidden = true)
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(newsService.searchNews(keyword, pageable));
    }


    @Operation(summary = "인기 뉴스 목록 조회", description = "AI가 분석한 중요도 점수(score)가 높은 순으로 뉴스 목록을 조회합니다.")
    @GetMapping("/popular")
    public ResponseEntity<Page<NewsResponseDto>> getPopularNews(
            @Parameter(hidden = true)
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(newsService.getPopularNews(pageable));
    }

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


    @Operation(summary = "카테고리별 뉴스 조회", description = "Naver API 검색 키워드(카테고리)별로 뉴스를 조회합니다.")
    @GetMapping("/category")
    public ResponseEntity<Page<NewsResponseDto>> getNewsByCategory(
            @Parameter(description = "카테고리명 (검색 키워드)", required = true, example = "AI")
            @RequestParam String name,
            @Parameter(hidden = true)
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(newsService.getNewsByCategory(name, pageable));
    }


    @Operation(summary = "카테고리 목록 조회", description = "사용 가능한 모든 카테고리(검색 키워드) 목록을 조회합니다.")
    @GetMapping("/categories")
    public ResponseEntity<java.util.List<String>> getCategories() {
        return ResponseEntity.ok(newsService.getCategories());
    }


    @Operation(summary = "소스별 뉴스 조회", description = "뉴스 출처(Naver, GeekNews 등)별로 뉴스를 조회합니다.")
    @GetMapping("/source")
    public ResponseEntity<Page<NewsResponseDto>> getNewsBySource(
            @Parameter(description = "소스명", required = true, example = "GeekNews")
            @RequestParam String name,
            @Parameter(hidden = true)
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(newsService.getNewsBySource(name, pageable));
    }


    @Operation(summary = "소스 목록 조회", description = "사용 가능한 모든 뉴스 소스 목록을 조회합니다.")
    @GetMapping("/sources")
    public ResponseEntity<java.util.List<String>> getSources() {
        return ResponseEntity.ok(newsService.getSources());
    }
}