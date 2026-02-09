package com.example.trendstream.dto;

import com.example.trendstream.domain.entity.Subscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class SubscriptionDto {


    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String email;
        private String name;
        private String keyword;
    }


    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String keyword;
        private LocalDateTime createdAt;
        private LocalDateTime lastNotifiedAt;

        public static Response from(Subscription subscription) {
            return Response.builder()
                    .id(subscription.getId())
                    .keyword(subscription.getKeyword())
                    .createdAt(subscription.getCreatedAt())
                    .lastNotifiedAt(subscription.getLastNotifiedAt())
                    .build();
        }
    }


    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSubscriptions {
        private String email;
        private String name;
        private Boolean notificationEnabled;
        private List<Response> subscriptions;
    }
}
