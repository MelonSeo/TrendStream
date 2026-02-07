package com.example.trendstream.service;

import com.example.trendstream.domain.entity.Subscription;
import com.example.trendstream.domain.entity.User;
import com.example.trendstream.dto.NewsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 알림 발송 서비스
 *
 * [현재 구현]
 * - 로그 출력 (실제 이메일 대신)
 * - Redis에 알림 기록 저장
 *
 * [확장 가능]
 * - 이메일 발송 (JavaMailSender)
 * - 웹 푸시 (FCM)
 * - Slack/Discord 웹훅
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 알림 중복 방지 TTL (같은 뉴스+사용자 조합 1시간 동안 재발송 안함) */
    private static final Duration DUPLICATE_PREVENTION_TTL = Duration.ofHours(1);

    /** Redis Key 접두사 */
    private static final String NOTIFICATION_KEY_PREFIX = "notification:sent:";
    private static final String NOTIFICATION_QUEUE_KEY = "notification:queue";

    /**
     * 알림 발송 (현재는 로그 + Redis 저장)
     */
    public void sendNotification(User user, Subscription subscription, NewsMessage news) {
        String dedupeKey = buildDedupeKey(user.getId(), news.getLink());

        // 중복 체크
        if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupeKey))) {
            log.debug(">>>> [Notification] 중복 알림 스킵: user={}, news={}",
                    user.getEmail(), news.getTitle());
            return;
        }

        // 알림 로그 출력 (실제 서비스에서는 이메일 발송)
        log.info(">>>> [Notification] 알림 발송!");
        log.info("     To: {}", user.getEmail());
        log.info("     Keyword: {}", subscription.getKeyword());
        log.info("     Title: {}", news.getTitle());
        log.info("     Link: {}", news.getLink());

        // Redis에 발송 기록 (중복 방지)
        redisTemplate.opsForValue().set(dedupeKey, "sent", DUPLICATE_PREVENTION_TTL);

        // 알림 큐에 추가 (나중에 배치 발송용)
        Map<String, String> notificationData = new HashMap<>();
        notificationData.put("email", user.getEmail());
        notificationData.put("keyword", subscription.getKeyword());
        notificationData.put("title", news.getTitle());
        notificationData.put("link", news.getLink());
        notificationData.put("source", news.getSource());

        redisTemplate.opsForList().rightPush(NOTIFICATION_QUEUE_KEY, notificationData);

        // 구독 정보 업데이트
        subscription.markNotified();
    }

    /**
     * 중복 방지용 키 생성
     */
    private String buildDedupeKey(Long userId, String newsLink) {
        return NOTIFICATION_KEY_PREFIX + userId + ":" + newsLink.hashCode();
    }

    /**
     * 대기 중인 알림 수 조회
     */
    public Long getPendingNotificationCount() {
        Long size = redisTemplate.opsForList().size(NOTIFICATION_QUEUE_KEY);
        return size != null ? size : 0L;
    }
}
