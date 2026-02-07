package com.example.trendstream.service.consumer;

import com.example.trendstream.domain.entity.Subscription;
import com.example.trendstream.dto.NewsMessage;
import com.example.trendstream.repository.SubscriptionRepository;
import com.example.trendstream.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 알림 Consumer
 *
 * [Consumer Group]
 * - notification-group (NewsConsumer, StatsConsumer와 별도)
 * - 같은 토픽(dev-news)을 독립적으로 소비
 *
 * [처리 흐름]
 * 1. 뉴스 메시지 수신
 * 2. 제목/설명에서 키워드 매칭
 * 3. 매칭된 구독자에게 알림 발송
 *
 * [Kafka 핵심 개념]
 * - 3개의 Consumer Group이 같은 토픽을 독립적으로 소비
 * - news-group: DB 저장
 * - stats-group: 통계 집계
 * - notification-group: 알림 발송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConsumer {

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;

    /** 캐시된 활성 키워드 목록 (성능 최적화) */
    private Set<String> cachedKeywords = Set.of();
    private long lastKeywordRefresh = 0;
    private static final long KEYWORD_REFRESH_INTERVAL = 60_000; // 1분

    @KafkaListener(
            topics = "dev-news",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processNotification(NewsMessage message) {
        try {
            // 키워드 캐시 갱신 (1분마다)
            refreshKeywordsIfNeeded();

            // 뉴스 텍스트 추출 (제목 + 설명)
            String newsText = (message.getTitle() + " " +
                    (message.getDescription() != null ? message.getDescription() : ""))
                    .toLowerCase();

            // 매칭되는 키워드 찾기
            for (String keyword : cachedKeywords) {
                if (newsText.contains(keyword.toLowerCase())) {
                    // 해당 키워드 구독자들에게 알림
                    notifySubscribers(keyword, message);
                }
            }

        } catch (Exception e) {
            log.error(">>>> [NotificationConsumer] 처리 실패: {}", e.getMessage());
        }
    }

    /**
     * 키워드 구독자들에게 알림 발송
     */
    private void notifySubscribers(String keyword, NewsMessage message) {
        List<Subscription> subscriptions = subscriptionRepository
                .findByKeywordWithActiveUsers(keyword);

        for (Subscription subscription : subscriptions) {
            notificationService.sendNotification(
                    subscription.getUser(),
                    subscription,
                    message
            );
        }

        if (!subscriptions.isEmpty()) {
            log.info(">>>> [NotificationConsumer] 키워드 '{}' 매칭: {}명에게 알림",
                    keyword, subscriptions.size());
        }
    }

    /**
     * 활성 키워드 목록 캐시 갱신
     */
    private void refreshKeywordsIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastKeywordRefresh > KEYWORD_REFRESH_INTERVAL) {
            List<String> keywords = subscriptionRepository.findAllActiveKeywords();
            cachedKeywords = Set.copyOf(keywords);
            lastKeywordRefresh = now;
            log.debug(">>>> [NotificationConsumer] 키워드 캐시 갱신: {}개", keywords.size());
        }
    }
}
