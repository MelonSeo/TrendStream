package com.example.trendstream.service;

import com.example.trendstream.domain.entity.Subscription;
import com.example.trendstream.domain.entity.User;
import com.example.trendstream.dto.NewsMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 알림 발송 서비스
 *
 * [구현 기능]
 * - Gmail SMTP를 통한 이메일 발송
 * - Redis 중복 방지 (같은 뉴스+사용자 1시간 내 재발송 안함)
 * - 비동기 발송 (@Async)
 *
 * [확장 가능]
 * - 웹 푸시 (FCM)
 * - Slack/Discord 웹훅
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JavaMailSender mailSender;

    @Value("${mail.from.name:TrendStream}")
    private String fromName;

    @Value("${mail.from.address:noreply@trendstream.com}")
    private String fromAddress;

    /** 알림 중복 방지 TTL (같은 뉴스+사용자 조합 1시간 동안 재발송 안함) */
    private static final Duration DUPLICATE_PREVENTION_TTL = Duration.ofHours(1);

    /** Redis Key 접두사 */
    private static final String NOTIFICATION_KEY_PREFIX = "notification:sent:";
    private static final String NOTIFICATION_QUEUE_KEY = "notification:queue";

    /**
     * 알림 발송 (이메일 + Redis 저장)
     */
    @Async
    public void sendNotification(User user, Subscription subscription, NewsMessage news) {
        String dedupeKey = buildDedupeKey(user.getId(), news.getLink());

        // 중복 체크
        if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupeKey))) {
            log.debug(">>>> [Notification] 중복 알림 스킵: user={}, news={}",
                    user.getEmail(), news.getTitle());
            return;
        }

        // 이메일 발송
        try {
            sendEmail(user, subscription, news);
            log.info(">>>> [Notification] 이메일 발송 성공: {} -> {}",
                    subscription.getKeyword(), user.getEmail());
        } catch (Exception e) {
            log.error(">>>> [Notification] 이메일 발송 실패: {}", e.getMessage());
            // 실패해도 Redis에 기록하지 않아 재시도 가능
            return;
        }

        // Redis에 발송 기록 (중복 방지)
        redisTemplate.opsForValue().set(dedupeKey, "sent", DUPLICATE_PREVENTION_TTL);

        // 알림 큐에 추가 (통계/로그용)
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
     * 이메일 발송
     */
    private void sendEmail(User user, Subscription subscription, NewsMessage news) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(String.format("%s <%s>", fromName, fromAddress));
        helper.setTo(user.getEmail());
        helper.setSubject(String.format("[TrendStream] '%s' 관련 새 뉴스가 도착했습니다!", subscription.getKeyword()));

        String htmlContent = buildEmailHtml(user, subscription, news);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    /**
     * 이메일 HTML 템플릿 생성
     */
    private String buildEmailHtml(User user, Subscription subscription, NewsMessage news) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; border-radius: 10px 10px 0 0; }
                    .header h1 { margin: 0; font-size: 24px; }
                    .content { background: #f9fafb; padding: 30px; border: 1px solid #e5e7eb; }
                    .keyword-badge { display: inline-block; background: #dbeafe; color: #1d4ed8; padding: 4px 12px; border-radius: 20px; font-size: 14px; font-weight: 600; }
                    .news-card { background: white; border-radius: 8px; padding: 20px; margin-top: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .news-title { font-size: 18px; font-weight: 600; color: #1f2937; margin-bottom: 10px; }
                    .news-source { font-size: 12px; color: #6b7280; margin-bottom: 15px; }
                    .news-link { display: inline-block; background: #3b82f6; color: white; padding: 10px 20px; border-radius: 6px; text-decoration: none; font-weight: 500; }
                    .news-link:hover { background: #2563eb; }
                    .footer { text-align: center; padding: 20px; color: #9ca3af; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>TrendStream</h1>
                        <p style="margin: 10px 0 0 0; opacity: 0.9;">AI 기반 IT 뉴스 알림</p>
                    </div>
                    <div class="content">
                        <p>안녕하세요, <strong>%s</strong>님!</p>
                        <p>구독하신 키워드 <span class="keyword-badge">%s</span> 관련 새 뉴스가 도착했습니다.</p>

                        <div class="news-card">
                            <div class="news-title">%s</div>
                            <div class="news-source">출처: %s</div>
                            <a href="%s" class="news-link" target="_blank">뉴스 보러 가기</a>
                        </div>
                    </div>
                    <div class="footer">
                        <p>이 메일은 TrendStream 키워드 구독 알림입니다.</p>
                        <p>구독 취소를 원하시면 TrendStream 웹사이트에서 관리해주세요.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                user.getName(),
                subscription.getKeyword(),
                escapeHtml(news.getTitle()),
                news.getSource(),
                news.getLink()
        );
    }

    /**
     * HTML 이스케이프
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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
