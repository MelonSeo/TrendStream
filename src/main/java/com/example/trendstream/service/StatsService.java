package com.example.trendstream.service;

import com.example.trendstream.dto.StatsResponseDto;
import com.example.trendstream.dto.StatsResponseDto.*;
import com.example.trendstream.repository.NewsStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final NewsStatsRepository newsStatsRepository;

    /**
     * 대시보드 종합 통계
     */
    public Dashboard getDashboard() {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);

        // 오늘 총 뉴스 수
        Long totalToday = calculateDailyTotal(today);

        // 최근 7일 총 뉴스 수
        Long totalWeek = calculateTotalSince(weekAgo);

        // 소스별 통계 (7일)
        List<SourceStats> sourceStats = getSourceStats(7);

        // 오늘 시간별 통계
        List<HourlyStats> hourlyStats = getHourlyStats(today);

        // 최근 7일 일별 통계
        List<DailyStats> dailyStats = getDailyStats(7);

        return Dashboard.builder()
                .totalToday(totalToday)
                .totalWeek(totalWeek)
                .sourceStats(sourceStats)
                .hourlyStats(hourlyStats)
                .dailyStats(dailyStats)
                .build();
    }

    /**
     * 소스별 통계 (최근 N일)
     */
    public List<SourceStats> getSourceStats(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return newsStatsRepository.findSourceStatsSince(startDate).stream()
                .map(row -> SourceStats.builder()
                        .source((String) row[0])
                        .count(convertToLong(row[1]))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 시간별 통계 (특정 날짜)
     */
    public List<HourlyStats> getHourlyStats(LocalDate date) {
        return newsStatsRepository.findHourlyStatsByDate(date).stream()
                .map(row -> HourlyStats.builder()
                        .hour((Integer) row[0])
                        .count(convertToLong(row[1]))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 일별 통계 (최근 N일)
     */
    public List<DailyStats> getDailyStats(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return newsStatsRepository.findDailyTotalsSince(startDate).stream()
                .map(row -> DailyStats.builder()
                        .date(row[0] instanceof java.sql.Date
                                ? ((java.sql.Date) row[0]).toLocalDate()
                                : (LocalDate) row[0])
                        .count(convertToLong(row[1]))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 특정 날짜 총 뉴스 수
     */
    private Long calculateDailyTotal(LocalDate date) {
        return newsStatsRepository.findDailyStatsByDate(date).stream()
                .mapToLong(row -> convertToLong(row[1]))
                .sum();
    }

    /**
     * 특정 날짜 이후 총 뉴스 수
     */
    private Long calculateTotalSince(LocalDate startDate) {
        return newsStatsRepository.findSourceStatsSince(startDate).stream()
                .mapToLong(row -> convertToLong(row[1]))
                .sum();
    }

    /**
     * Native Query 결과를 Long으로 변환
     * (MySQL은 BigDecimal, H2는 Long 등 DB마다 다름)
     */
    private Long convertToLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Long) return (Long) value;
        if (value instanceof BigDecimal) return ((BigDecimal) value).longValue();
        if (value instanceof Number) return ((Number) value).longValue();
        return 0L;
    }
}
