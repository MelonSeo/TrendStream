package com.example.trendstream.repository;

import com.example.trendstream.domain.entity.Subscription;
import com.example.trendstream.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * 특정 키워드를 구독 중인 사용자 조회
     * (알림 활성화된 사용자만)
     */
    @Query("""
            SELECT s FROM Subscription s
            JOIN FETCH s.user u
            WHERE LOWER(s.keyword) = LOWER(:keyword)
            AND u.notificationEnabled = true
            """)
    List<Subscription> findByKeywordWithActiveUsers(@Param("keyword") String keyword);

    /**
     * 특정 사용자의 구독 목록
     */
    List<Subscription> findByUser(User user);

    /**
     * 중복 구독 확인
     */
    Optional<Subscription> findByUserAndKeyword(User user, String keyword);

    /**
     * 모든 활성 키워드 목록 (중복 제거)
     */
    @Query("SELECT DISTINCT LOWER(s.keyword) FROM Subscription s")
    List<String> findAllActiveKeywords();
}
