package com.example.trendstream.service.producer;

import com.example.trendstream.domain.enums.NewsType;
import com.example.trendstream.dto.NewsMessage;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.trendstream.util.HtmlUtils;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lobsters RSS 뉴스 Producer
 *
 * [특징]
 * - Hacker News 대안으로 시작된 기술 링크 공유 커뮤니티
 * - 초대제 가입으로 스팸이 적고 고품질 콘텐츠
 * - 프로그래밍, 시스템 관리, 보안 등 기술 주제 중심
 *
 * [수집 주기]
 * - 15분마다 최신 글 수집
 * - 메모리 캐시로 중복 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LobstersProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String LOBSTERS_RSS = "https://lobste.rs/rss";
    private static final String TOPIC = "dev-news";

    /** 최근 전송한 링크 캐시 (중복 방지) */
    private final Set<String> recentLinks = new HashSet<>();
    private static final int MAX_CACHE_SIZE = 300;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    @Scheduled(fixedRate = 900000) // 15분마다
    public void fetchLobstersNews() {
        log.info(">>>> [Lobsters] RSS 뉴스 수집 시작");

        try {
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(new URL(LOBSTERS_RSS)));

            int count = 0;
            for (SyndEntry entry : feed.getEntries()) {
                String link = entry.getLink();

                // 캐시 기반 중복 방지
                if (recentLinks.contains(link)) {
                    continue;
                }

                // 날짜 변환
                String pubDateStr = formatDate(entry.getPublishedDate());

                // 제목, 설명 정제 (HTML 태그 + 엔티티 디코딩)
                String cleanTitle = HtmlUtils.clean(entry.getTitle());
                String cleanDesc = entry.getDescription() != null
                        ? HtmlUtils.clean(entry.getDescription().getValue())
                        : "";

                NewsMessage message = NewsMessage.builder()
                        .title(cleanTitle)
                        .link(link)
                        .description(cleanDesc)
                        .source("Lobsters")
                        .type(NewsType.COMMUNITY)
                        .pubDateStr(pubDateStr)
                        .searchKeyword(null) // IT 뉴스 전체
                        .build();

                kafkaTemplate.send(TOPIC, message);
                addToCache(link);
                count++;
            }

            log.info(">>>> [Lobsters] 뉴스 {}개 Kafka 전송 완료", count);

        } catch (Exception e) {
            log.error(">>>> [Lobsters] RSS 파싱 실패: {}", e.getMessage());
        }
    }

    private String formatDate(Date date) {
        if (date == null) {
            return java.time.ZonedDateTime.now()
                    .format(DATE_FORMATTER);
        }
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .format(DATE_FORMATTER);
    }

    private void addToCache(String link) {
        if (recentLinks.size() >= MAX_CACHE_SIZE) {
            recentLinks.clear();
        }
        recentLinks.add(link);
    }
}
