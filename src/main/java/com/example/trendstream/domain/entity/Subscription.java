package com.example.trendstream.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 키워드 구독 엔티티
 *
 * [동작 방식]
 * 1. 사용자가 키워드(예: "Spring", "Kubernetes")를 구독
 * 2. 새 뉴스가 들어오면 NotificationConsumer가 키워드 매칭
 * 3. 매칭되면 해당 사용자에게 알림
 */
@Entity
@Table(name = "subscriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "keyword"}),
        indexes = @Index(name = "idx_subscription_keyword", columnList = "keyword"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 구독 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 구독 키워드 (소문자 정규화) */
    @Column(nullable = false, length = 50)
    private String keyword;

    /** 구독일 */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 마지막 알림 발송 시간 (중복 알림 방지용) */
    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    /**
     * 알림 발송 기록
     */
    public void markNotified() {
        this.lastNotifiedAt = LocalDateTime.now();
    }
}
