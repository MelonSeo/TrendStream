package com.example.trendstream.controller;

import com.example.trendstream.dto.StatsResponseDto.*;
import com.example.trendstream.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Stats", description = "뉴스 통계 API")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "대시보드 종합 통계", description = "오늘/주간 통계, 소스별/시간별/일별 통계 종합")
    @GetMapping("/dashboard")
    public ResponseEntity<Dashboard> getDashboard() {
        return ResponseEntity.ok(statsService.getDashboard());
    }

    @Operation(summary = "소스별 통계", description = "최근 N일간 소스별 뉴스 수")
    @GetMapping("/sources")
    public ResponseEntity<List<SourceStats>> getSourceStats(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(statsService.getSourceStats(days));
    }

    @Operation(summary = "시간별 통계", description = "특정 날짜의 시간별 뉴스 수")
    @GetMapping("/hourly")
    public ResponseEntity<List<HourlyStats>> getHourlyStats(
            @RequestParam(required = false) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return ResponseEntity.ok(statsService.getHourlyStats(date));
    }

    @Operation(summary = "일별 통계", description = "최근 N일간 일별 뉴스 수")
    @GetMapping("/daily")
    public ResponseEntity<List<DailyStats>> getDailyStats(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(statsService.getDailyStats(days));
    }
}
