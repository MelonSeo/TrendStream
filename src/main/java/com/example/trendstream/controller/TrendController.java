package com.example.trendstream.controller;

import com.example.trendstream.dto.TrendResponseDto;
import com.example.trendstream.service.TrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 트렌드 REST API 컨트롤러
 *
 * [제공 API]
 * - GET /api/trends : 기간별 트렌드 키워드 순위 조회
 */
@Tag(name = "Trend API", description = "실시간 트렌드 키워드 분석 API")
@RestController
@RequestMapping("/api/trends")
@RequiredArgsConstructor
public class TrendController {

    private final TrendService trendService;

    /**
     * 트렌드 키워드 순위 조회
     *
     * [요청 예시]
     * - GET /api/trends                        -> 24시간, 상위 10개
     * - GET /api/trends?period=7d&limit=5      -> 7일간, 상위 5개
     * - GET /api/trends?period=30d&limit=20    -> 30일간, 상위 20개
     *
     * @param period 집계 기간 ("24h", "7d", "30d")
     * @param limit 상위 N개 키워드
     * @return 트렌드 키워드 순위 + 관련 뉴스
     */
    @Operation(summary = "트렌드 키워드 순위 조회",
            description = "지정된 기간 동안 가장 많이 등장한 키워드를 빈도순으로 조회합니다. 각 키워드별 관련 뉴스 3개를 포함합니다.")
    @GetMapping
    public ResponseEntity<List<TrendResponseDto>> getTopTrends(
            @Parameter(description = "집계 기간 (24h, 7d, 30d)", example = "24h")
            @RequestParam(defaultValue = "24h") String period,
            @Parameter(description = "상위 키워드 개수", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(trendService.getTopTrends(period, limit));
    }
}
