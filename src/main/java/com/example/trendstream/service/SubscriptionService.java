package com.example.trendstream.service;

import com.example.trendstream.domain.entity.Subscription;
import com.example.trendstream.domain.entity.User;
import com.example.trendstream.dto.SubscriptionDto.*;
import com.example.trendstream.repository.SubscriptionRepository;
import com.example.trendstream.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * 키워드 구독 (사용자 없으면 자동 생성)
     */
    @Transactional
    public Response subscribe(CreateRequest request) {
        // 1. 사용자 조회 또는 생성
        User user = userRepository.findByEmail(request.getEmail())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(request.getEmail())
                            .name(request.getName())
                            .build();
                    return userRepository.save(newUser);
                });

        // 2. 키워드 정규화 (소문자)
        String normalizedKeyword = request.getKeyword().strip().toLowerCase(Locale.ROOT);

        // 3. 중복 구독 확인
        if (subscriptionRepository.findByUserAndKeyword(user, normalizedKeyword).isPresent()) {
            throw new IllegalArgumentException("이미 구독 중인 키워드입니다: " + normalizedKeyword);
        }

        // 4. 구독 생성
        Subscription subscription = Subscription.builder()
                .user(user)
                .keyword(normalizedKeyword)
                .build();

        subscriptionRepository.save(subscription);

        log.info(">>>> [Subscription] 구독 추가: {} -> {}", user.getEmail(), normalizedKeyword);

        return Response.from(subscription);
    }

    /**
     * 구독 취소
     */
    @Transactional
    public void unsubscribe(String email, String keyword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + email));

        String normalizedKeyword = keyword.strip().toLowerCase(Locale.ROOT);

        Subscription subscription = subscriptionRepository.findByUserAndKeyword(user, normalizedKeyword)
                .orElseThrow(() -> new IllegalArgumentException("구독 정보를 찾을 수 없습니다: " + keyword));

        subscriptionRepository.delete(subscription);

        log.info(">>>> [Subscription] 구독 취소: {} -> {}", email, normalizedKeyword);
    }

    /**
     * 사용자 구독 목록 조회 (존재하지 않는 사용자는 빈 결과 반환)
     */
    @Transactional(readOnly = true)
    public UserSubscriptions getUserSubscriptions(String email) {
        return userRepository.findByEmail(email)
                .map(user -> {
                    List<Response> subscriptions = subscriptionRepository.findByUser(user).stream()
                            .map(Response::from)
                            .collect(Collectors.toList());

                    return UserSubscriptions.builder()
                            .email(user.getEmail())
                            .name(user.getName())
                            .notificationEnabled(user.getNotificationEnabled())
                            .subscriptions(subscriptions)
                            .build();
                })
                .orElse(UserSubscriptions.builder()
                        .email(email)
                        .name("")
                        .notificationEnabled(true)
                        .subscriptions(List.of())
                        .build());
    }

    /**
     * 알림 설정 토글
     */
    @Transactional
    public void toggleNotification(String email, boolean enabled) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + email));

        // User 엔티티에 setter 추가 필요
        // user.setNotificationEnabled(enabled);
        // 또는 별도 메서드로 처리

        log.info(">>>> [Subscription] 알림 설정 변경: {} -> {}", email, enabled);
    }

    /**
     * 활성 키워드 목록 조회
     */
    @Transactional(readOnly = true)
    public List<String> getAllActiveKeywords() {
        return subscriptionRepository.findAllActiveKeywords();
    }
}
