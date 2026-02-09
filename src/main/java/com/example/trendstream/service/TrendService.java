package com.example.trendstream.service;

import com.example.trendstream.dto.TrendResponseDto;
import com.example.trendstream.repository.NewsTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrendService {

    private final NewsTagRepository newsTagRepository;

    private static final int RELATED_NEWS_LIMIT = 3;

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

    private LocalDateTime parsePeriod(String period) {
        LocalDateTime now = LocalDateTime.now();

        return switch (period) {
            case "7d" -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            default -> now.minusHours(24); // "24h" 및 기본값
        };
    }
}
