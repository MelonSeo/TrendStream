package com.example.trendstream.controller;

import com.example.trendstream.dto.SubscriptionDto.*;
import com.example.trendstream.service.NotificationService;
import com.example.trendstream.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Subscription", description = "키워드 구독 API")
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;

    @Operation(summary = "키워드 구독", description = "특정 키워드를 구독합니다. 새 뉴스 발생 시 알림을 받습니다.")
    @PostMapping
    public ResponseEntity<Response> subscribe(@RequestBody CreateRequest request) {
        return ResponseEntity.ok(subscriptionService.subscribe(request));
    }

    @Operation(summary = "구독 취소", description = "키워드 구독을 취소합니다.")
    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(
            @RequestParam String email,
            @RequestParam String keyword) {
        subscriptionService.unsubscribe(email, keyword);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 구독 목록", description = "사용자의 구독 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<UserSubscriptions> getSubscriptions(@RequestParam String email) {
        return ResponseEntity.ok(subscriptionService.getUserSubscriptions(email));
    }

    @Operation(summary = "활성 키워드 목록", description = "현재 구독 중인 모든 키워드 목록")
    @GetMapping("/keywords")
    public ResponseEntity<List<String>> getActiveKeywords() {
        return ResponseEntity.ok(subscriptionService.getAllActiveKeywords());
    }

    @Operation(summary = "대기 중 알림 수", description = "발송 대기 중인 알림 수를 조회합니다.")
    @GetMapping("/notifications/pending")
    public ResponseEntity<Map<String, Long>> getPendingNotifications() {
        Long count = notificationService.getPendingNotificationCount();
        return ResponseEntity.ok(Map.of("pending", count));
    }
}
