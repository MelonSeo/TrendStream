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
import com.example.trendstream.util.SpamFilter;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * GeekNews (긱뉴스) Producer
 *
 * [RSS 스펙]
 * - URL: https://news.hada.io/rss/news (Feedburner로 리다이렉트)
 * - 형식: Atom 1.0
 * - 필드: title, link, content, published
 *
 * [수집 전략]
 * - 10분마다 RSS 피드 전체 조회
 * - 중복 방지를 위한 링크 캐시 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeekNewsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 원본 URL이 Feedburner로 리다이렉트되므로 직접 사용
    // (Java는 HTTPS→HTTP 리다이렉트를 보안상 자동으로 따라가지 않음)
    private static final String GEEK_NEWS_RSS = "https://feeds.feedburner.com/geeknews-feed";

    // 중복 방지 캐시 (링크 기준)
    private final Set<String> sentLinkCache = Collections.synchronizedSet(new HashSet<>());

    // Naver API 형식과 맞추기 위한 날짜 포맷터
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
                    .withZone(ZoneId.of("Asia/Seoul"));

    /**
     * 10분마다 GeekNews RSS 수집
     */
    @Scheduled(fixedDelay = 600000) // 10분
    public void crawlGeekNews() {
        log.info(">>>> [GeekNewsProducer] GeekNews 수집 시작...");

        try {
            // 1. RSS 피드 파싱 (Feedburner HTTPS 직접 호출)
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(new URL(GEEK_NEWS_RSS)));

            int count = 0;

            // 2. 각 엔트리 처리
            for (SyndEntry entry : feed.getEntries()) {
                String link = entry.getLink();

                // 이미 전송한 링크면 스킵
                if (sentLinkCache.contains(link)) {
                    continue;
                }

                // 3. 제목, 설명 정제 (HTML 태그 + 엔티티 디코딩)
                String cleanTitle = HtmlUtils.clean(entry.getTitle());
                String cleanDesc = "";

                // Atom의 content 또는 description 추출
                if (entry.getContents() != null && !entry.getContents().isEmpty()) {
                    cleanDesc = HtmlUtils.truncate(
                            HtmlUtils.clean(entry.getContents().get(0).getValue()), 500);
                } else if (entry.getDescription() != null) {
                    cleanDesc = HtmlUtils.clean(entry.getDescription().getValue());
                }

                // 날짜 변환
                String pubDateStr;
                if (entry.getPublishedDate() != null) {
                    pubDateStr = DATE_FORMATTER.format(
                            entry.getPublishedDate().toInstant());
                } else {
                    pubDateStr = DATE_FORMATTER.format(java.time.Instant.now());
                }

                // 스팸 필터링
                if (SpamFilter.isSpam(cleanTitle, cleanDesc)) {
                    log.debug(">>>> [GeekNews] 스팸 필터링: {}", cleanTitle);
                    continue;
                }

                NewsMessage message = NewsMessage.builder()
                        .title(cleanTitle)
                        .link(link)
                        .description(cleanDesc)
                        .source("GeekNews")
                        .type(NewsType.COMMUNITY)
                        .pubDateStr(pubDateStr)
                        // searchKeyword는 null (IT 뉴스 전체이므로 별도 카테고리 불필요)
                        .build();

                kafkaTemplate.send("dev-news", message);
                sentLinkCache.add(link);
                count++;
            }

            if (count > 0) {
                log.info(">>>> [GeekNewsProducer] {}건의 새로운 뉴스 전송 완료", count);
            } else {
                log.info(">>>> [GeekNewsProducer] 새로운 뉴스가 없습니다.");
            }

            // 캐시 크기 관리 (최대 500개 유지)
            if (sentLinkCache.size() > 500) {
                sentLinkCache.clear();
                log.info(">>>> [GeekNewsProducer] 캐시 초기화");
            }

        } catch (Exception e) {
            log.error(">>>> [에러] GeekNews 수집 실패: {}", e.getMessage(), e);
        }
    }
}
