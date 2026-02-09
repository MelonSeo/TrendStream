package com.example.trendstream.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


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


    public void markNotified() {
        this.lastNotifiedAt = LocalDateTime.now();
    }
}
