package com.example.trendstream.repository;

import com.example.trendstream.domain.entity.NewsStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NewsStatsRepository extends JpaRepository<NewsStats, Long> {

    /**
     * 특정 소스의 시간별 통계 조회 (upsert용)
     */
    Optional<NewsStats> findBySourceAndStatDateAndStatHour(String source, LocalDate statDate, Integer statHour);

    /**
     * 특정 날짜의 소스별 일간 합계
     */
    @Query(value = """
            SELECT source, SUM(count) as total
            FROM news_stats
            WHERE stat_date = :date
            GROUP BY source
            ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> findDailyStatsByDate(@Param("date") LocalDate date);

    /**
     * 특정 날짜의 시간별 통계 (전체 소스)
     */
    @Query(value = """
            SELECT stat_hour, SUM(count) as total
            FROM news_stats
            WHERE stat_date = :date
            GROUP BY stat_hour
            ORDER BY stat_hour
            """, nativeQuery = true)
    List<Object[]> findHourlyStatsByDate(@Param("date") LocalDate date);

    /**
     * 최근 N일간 소스별 총 뉴스 수
     */
    @Query(value = """
            SELECT source, SUM(count) as total
            FROM news_stats
            WHERE stat_date >= :startDate
            GROUP BY source
            ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> findSourceStatsSince(@Param("startDate") LocalDate startDate);

    /**
     * 최근 N일간 일별 총 뉴스 수
     */
    @Query(value = """
            SELECT stat_date, SUM(count) as total
            FROM news_stats
            WHERE stat_date >= :startDate
            GROUP BY stat_date
            ORDER BY stat_date
            """, nativeQuery = true)
    List<Object[]> findDailyTotalsSince(@Param("startDate") LocalDate startDate);
}
