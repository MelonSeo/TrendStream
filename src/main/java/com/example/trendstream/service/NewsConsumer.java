package com.example.trendstream.service;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.domain.enums.NewsType;
import com.example.trendstream.repository.NewsRepository;
import com.example.trendstream.dto.NewsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Kafka 뉴스 메시지 소비자
 *
 * [역할 변경 - 배치 처리 도입]
 * - AS-IS: 메시지 수신 → AI 분석 → DB 저장
 * - TO-BE: 메시지 수신 → DB 저장만 (AI 분석은 스케줄러가 배치로 처리)
 *
 * [AI 분석 분리 이유]
 * - API 호출 최적화: 5개씩 묶어서 1회 호출 (80% 절약)
 * - 무료 한도 효율적 사용
 * - 분석 실패해도 뉴스 데이터는 보존
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsConsumer {

    private final NewsRepository newsRepository;

    private static final DateTimeFormatter NAVER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    /**
     * Kafka 메시지 소비 - DB 저장만 담당
     *
     * [처리 흐름]
     * 1. 중복 체크 (link 기준)
     * 2. 날짜 파싱
     * 3. DB 저장 (aiResult = null)
     * 4. AI 분석은 NewsAnalysisScheduler가 별도로 처리
     */
    @KafkaListener(topics = "dev-news", groupId = "news-group")
    @Transactional
    public void consumeNews(NewsMessage message) {
        log.info(">>>> [Consumer] 뉴스 수신: {}", message.getTitle());

        // 1. 이미 DB에 있는 뉴스인지 확인 (중복 방지)
        if (newsRepository.existsByLink(message.getLink())) {
            log.info(">>>> [Skip] 이미 존재하는 뉴스입니다.");
            return;
        }

        // 2. 날짜 파싱
        LocalDateTime publishedAt;
        try {
            publishedAt = LocalDateTime.parse(message.getPubDateStr(), NAVER_DATE_FORMAT);
        } catch (Exception e) {
            log.warn("날짜 파싱 실패, 현재 시간으로 대체: {}", message.getPubDateStr());
            publishedAt = LocalDateTime.now();
        }

        // 3. DB에 저장 (AI 분석 없이, aiResult = null)
        News news = News.builder()
                .title(message.getTitle())
                .link(message.getLink())
                .description(message.getDescription())
                .source("Naver Open API")
                .type(NewsType.NEWS)
                .pubDate(publishedAt)
                .aiResult(null)  // AI 분석은 스케줄러가 배치로 처리
                .searchKeyword(message.getSearchKeyword())
                .build();

        newsRepository.save(news);
        log.info(">>>> [DB 저장 완료] ID: {} (AI 분석 대기중)", news.getId());
    }
}