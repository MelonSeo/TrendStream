package com.example.trendstream.service.consumer;

import com.example.trendstream.domain.entity.NewsStats;
import com.example.trendstream.dto.NewsMessage;
import com.example.trendstream.repository.NewsStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 통계 집계 Consumer
 *
 * [Consumer Group]
 * - stats-group (NewsConsumer의 news-group과 별도)
 * - 같은 토픽(dev-news)을 독립적으로 소비
 *
 * [처리 내용]
 * - 소스별, 시간별 뉴스 카운트 집계
 * - news_stats 테이블에 upsert
 *
 * [Kafka 핵심 개념]
 * - 하나의 토픽을 여러 Consumer Group이 독립적으로 소비 가능
 * - 각 그룹은 모든 메시지를 받음 (브로드캐스트 효과)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsConsumer {

    private final NewsStatsRepository newsStatsRepository;

    /**
     * 뉴스 메시지 수신 → 통계 집계
     *
     * groupId가 다르므로 NewsConsumer와 독립적으로 동작
     */
    @KafkaListener(
            topics = "dev-news",
            groupId = "stats-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void aggregateStats(NewsMessage message) {
        try {
            String source = message.getSource();
            LocalDateTime now = LocalDateTime.now();
            LocalDate today = now.toLocalDate();
            int hour = now.getHour();

            // Upsert: 있으면 증가, 없으면 생성
            NewsStats stats = newsStatsRepository
                    .findBySourceAndStatDateAndStatHour(source, today, hour)
                    .orElse(null);

            if (stats != null) {
                stats.incrementCount();
            } else {
                stats = NewsStats.createNew(source, today, hour);
            }

            newsStatsRepository.save(stats);

            log.debug(">>>> [StatsConsumer] 통계 집계: {} - {}시 (count: {})",
                    source, hour, stats.getCount());

        } catch (Exception e) {
            log.error(">>>> [StatsConsumer] 집계 실패: {}", e.getMessage());
        }
    }
}
