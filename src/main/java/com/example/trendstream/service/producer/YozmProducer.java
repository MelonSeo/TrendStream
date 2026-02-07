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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 요즘IT (Yozm) RSS 뉴스 Producer
 *
 * [특징]
 * - 위시켓에서 운영하는 IT 콘텐츠 플랫폼
 * - 개발, 디자인, 기획, 마케팅 등 IT 업계 전반의 양질의 콘텐츠
 * - 한국어 IT 뉴스/아티클 중심
 *
 * [수집 주기]
 * - 15분마다 최신 글 수집
 * - 메모리 캐시로 중복 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YozmProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String YOZM_RSS = "https://yozm.wishket.com/magazine/feed/";
    private static final String TOPIC = "dev-news";

    /** 최근 전송한 링크 캐시 (중복 방지) */
    private final Set<String> recentLinks = new HashSet<>();
    private static final int MAX_CACHE_SIZE = 300;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    @Scheduled(initialDelay = 60000, fixedRate = 900000) // 앱 시작 60초 후 첫 실행, 이후 15분마다
    public void fetchYozmNews() {
        log.info(">>>> [요즘IT] RSS 뉴스 수집 시작");

        try {
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(new URL(YOZM_RSS)));

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
                        ? HtmlUtils.truncate(HtmlUtils.clean(entry.getDescription().getValue()), 500)
                        : "";

                NewsMessage message = NewsMessage.builder()
                        .title(cleanTitle)
                        .link(link)
                        .description(cleanDesc)
                        .source("요즘IT")
                        .type(NewsType.NEWS)
                        .pubDateStr(pubDateStr)
                        .searchKeyword(null) // IT 콘텐츠 전체
                        .build();

                kafkaTemplate.send(TOPIC, message);
                addToCache(link);
                count++;
            }

            log.info(">>>> [요즘IT] 뉴스 {}개 Kafka 전송 완료", count);

        } catch (Exception e) {
            log.error(">>>> [요즘IT] RSS 파싱 실패: {}", e.getMessage());
        }
    }

    private String formatDate(Date date) {
        if (date == null) {
            return ZonedDateTime.now()
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
