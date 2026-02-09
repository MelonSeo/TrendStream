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


@Tag(name = "Trend API", description = "실시간 트렌드 키워드 분석 API")
@RestController
@RequestMapping("/api/trends")
@RequiredArgsConstructor
public class TrendController {

    private final TrendService trendService;


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
