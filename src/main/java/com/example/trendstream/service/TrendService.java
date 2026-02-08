package com.example.trendstream.service;

import com.example.trendstream.dto.TrendResponseDto;
import com.example.trendstream.repository.NewsTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 트렌드 분석 서비스
 *
 * [동작 방식]
 * 1. 기간(period)을 LocalDateTime으로 변환
 * 2. NewsTagRepository로 키워드 빈도 집계
 * 3. 각 키워드별 관련 뉴스 3개 조회
 * 4. TrendResponseDto로 조립하여 반환
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrendService {

    private final NewsTagRepository newsTagRepository;

    private static final int RELATED_NEWS_LIMIT = 3;

    /**
     * 상위 트렌드 키워드 조회
     *
     * [캐싱 미적용 사유]
     * - GenericJackson2JsonRedisSerializer가 List<DTO> 역직렬화 시 타입 정보 문제 발생
     * - 추후 Jackson2JsonRedisSerializer + 명시적 타입 지정으로 개선 예정
     *
     * @param period 기간 ("24h", "7d", "30d")
     * @param limit 상위 N개 키워드
     * @return 트렌드 키워드 + 관련 뉴스 목록
     */
    public List<TrendResponseDto> getTopTrends(String period, int limit) {
        LocalDateTime since = parsePeriod(period);

        List<Object[]> trendRows = newsTagRepository.findTopTrendingSince(since, limit);

        return trendRows.stream()
                .map(row -> {
                    String keyword = (String) row[0];
                    Long count = ((Number) row[1]).longValue();

                    List<TrendResponseDto.NewsSummary> relatedNews = getRelatedNews(keyword, since);

                    return TrendResponseDto.builder()
                            .keyword(keyword)
                            .count(count)
                            .relatedNews(relatedNews)
                            .build();
                })
                .toList();
    }

    private List<TrendResponseDto.NewsSummary> getRelatedNews(String keyword, LocalDateTime since) {
        List<Object[]> newsRows = newsTagRepository.findRecentNewsByTagName(
                keyword, since, RELATED_NEWS_LIMIT);

        return newsRows.stream()
                .map(row -> TrendResponseDto.NewsSummary.builder()
                        .id(((Number) row[0]).longValue())
                        .title((String) row[1])
                        .link((String) row[2])
                        .build())
                .toList();
    }

    /**
     * 기간 문자열을 LocalDateTime으로 변환
     *
     * @param period "24h", "7d", "30d"
     * @return 현재 시각 기준으로 과거 시점
     */
    private LocalDateTime parsePeriod(String period) {
        LocalDateTime now = LocalDateTime.now();

        return switch (period) {
            case "7d" -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            default -> now.minusHours(24); // "24h" 및 기본값
        };
    }
}
