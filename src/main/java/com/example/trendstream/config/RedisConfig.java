package com.example.trendstream.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 설정
 *
 * [용도]
 * 1. 알림 큐: 발송 대기 알림 저장
 * 2. 중복 방지: 최근 알림 발송 기록 캐싱
 * 3. API 응답 캐싱: 자주 조회되는 데이터 캐싱 (성능 최적화)
 *
 * [@EnableCaching 동작 원리]
 * - @Cacheable 메서드 호출 시 프록시가 캐시 먼저 확인
 * - 캐시 히트: DB 조회 없이 캐시된 값 반환
 * - 캐시 미스: 메서드 실행 후 결과를 캐시에 저장
 *
 * [캐시 전략]
 * - categories: 1시간 TTL (거의 변경 없음)
 * - sources: 1시간 TTL (거의 변경 없음)
 * - trends: 5분 TTL (주기적 갱신 필요)
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key는 String으로
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value는 JSON으로
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis 캐시 매니저 설정
     *
     * [TTL(Time To Live) 설정 이유]
     * - 무한 캐시 방지: 메모리 누수 예방
     * - 데이터 신선도 유지: 적절한 주기로 갱신
     * - 캐시별 차등 TTL: 데이터 특성에 맞게 설정
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정 (30분 TTL)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // 캐시별 개별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // categories: 거의 변경 없음 → 1시간 캐싱
        cacheConfigs.put("categories", defaultConfig.entryTtl(Duration.ofHours(1)));

        // sources: 거의 변경 없음 → 1시간 캐싱
        cacheConfigs.put("sources", defaultConfig.entryTtl(Duration.ofHours(1)));

        // trends: 실시간성 필요 → 5분 캐싱
        cacheConfigs.put("trends", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
