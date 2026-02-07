package com.example.trendstream.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자 엔티티
 *
 * [용도]
 * - 키워드 구독 기능의 주체
 * - 알림 수신 대상
 */
@Entity
@Table(name = "users",
        indexes = @Index(name = "idx_user_email", columnList = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이메일 (로그인 ID, 알림 수신용) */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** 이름 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 알림 활성화 여부 */
    @Column(name = "notification_enabled", nullable = false)
    @Builder.Default
    private Boolean notificationEnabled = true;

    /** 가입일 */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 구독 목록 */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Subscription> subscriptions = new ArrayList<>();

    /**
     * 구독 추가
     */
    public void addSubscription(Subscription subscription) {
        this.subscriptions.add(subscription);
        subscription.setUser(this);
    }
}
