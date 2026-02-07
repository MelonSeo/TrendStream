package com.example.trendstream.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 뉴스 통계 엔티티
 *
 * [집계 단위]
 * - 시간별 (hourly): 소스별 시간당 뉴스 수
 * - 일별 (daily): 소스별 하루 뉴스 수
 *
 * [사용처]
 * - 대시보드: 소스별 활성도 비교
 * - 트렌드: 시간대별 뉴스 유입량 분석
 */
@Entity
@Table(name = "news_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source", "stat_date", "stat_hour"}),
        indexes = {
                @Index(name = "idx_stats_source", columnList = "source"),
                @Index(name = "idx_stats_date", columnList = "stat_date")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NewsStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 뉴스 소스 (Naver, Hacker News, Velog 등) */
    @Column(nullable = false, length = 50)
    private String source;

    /** 통계 날짜 */
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    /** 통계 시간 (0~23) */
    @Column(name = "stat_hour", nullable = false)
    private Integer statHour;

    /** 해당 시간대 뉴스 수 */
    @Column(nullable = false)
    private Long count;

    /** 마지막 업데이트 시간 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 카운트 증가
     */
    public void incrementCount() {
        this.count++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 신규 통계 생성용 팩토리 메서드
     */
    public static NewsStats createNew(String source, LocalDate date, int hour) {
        return NewsStats.builder()
                .source(source)
                .statDate(date)
                .statHour(hour)
                .count(1L)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
