package com.example.trendstream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class StatsResponseDto {

    /**
     * 소스별 통계
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceStats {
        private String source;
        private Long count;
    }

    /**
     * 시간별 통계
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyStats {
        private Integer hour;
        private Long count;
    }

    /**
     * 일별 통계
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private LocalDate date;
        private Long count;
    }

    /**
     * 대시보드용 종합 통계
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dashboard {
        private Long totalToday;
        private Long totalWeek;
        private List<SourceStats> sourceStats;
        private List<HourlyStats> hourlyStats;
        private List<DailyStats> dailyStats;
    }
}
