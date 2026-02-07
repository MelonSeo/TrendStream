package com.example.trendstream.service.producer;

import com.example.trendstream.domain.enums.NewsType;
import com.example.trendstream.dto.HackerNewsDto;
import com.example.trendstream.dto.NewsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.trendstream.util.HtmlUtils;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Hacker News Producer
 *
 * [API 스펙]
 * - Base URL: https://hacker-news.firebaseio.com/v0/
 * - /newstories.json: 최신 스토리 ID 배열 (최대 500개)
 * - /item/{id}.json: 개별 아이템 상세
 *
 * [수집 전략]
 * - 10분마다 최신 스토리 30개 조회
 * - type=story이고 url이 있는 것만 수집 (Ask HN, Show HN without link 제외)
 * - 중복 방지를 위한 메모리 캐시 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HackerNewsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String HN_API_BASE = "https://hacker-news.firebaseio.com/v0";
    private static final int FETCH_COUNT = 50; // 한 번에 가져올 스토리 수

    // 중복 방지 캐시 (메모리)
    private final Set<Long> sentIdCache = Collections.synchronizedSet(new HashSet<>());

    // Naver API 형식과 맞추기 위한 날짜 포맷터
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
                    .withZone(ZoneId.of("Asia/Seoul"));

    /**
     * 10분마다 Hacker News 최신 스토리 수집
     */
    @Scheduled(fixedDelay = 600000) // 10분
    public void crawlHackerNews() {
        log.info(">>>> [HackerNewsProducer] Hacker News 수집 시작...");

        try {
            // 1. 최신 스토리 ID 목록 조회
            Long[] storyIds = restTemplate.getForObject(
                    HN_API_BASE + "/newstories.json", Long[].class);

            if (storyIds == null || storyIds.length == 0) {
                log.warn(">>>> [HackerNewsProducer] 스토리 ID 조회 실패");
                return;
            }

            int count = 0;
            int limit = Math.min(FETCH_COUNT, storyIds.length);

            // 2. 각 스토리 상세 조회
            for (int i = 0; i < limit; i++) {
                Long storyId = storyIds[i];

                // 이미 전송한 스토리면 스킵
                if (sentIdCache.contains(storyId)) {
                    continue;
                }

                try {
                    HackerNewsDto item = restTemplate.getForObject(
                            HN_API_BASE + "/item/" + storyId + ".json", HackerNewsDto.class);

                    // 유효한 스토리인지 확인 (type=story, url 있음)
                    if (item == null || !"story".equals(item.getType()) || item.getUrl() == null) {
                        continue;
                    }

                    // 3. 제목, 설명 정제 (HTML 태그 + 엔티티 디코딩)
                    String cleanTitle = HtmlUtils.clean(item.getTitle());
                    String cleanDesc = item.getText() != null
                            ? HtmlUtils.truncate(HtmlUtils.clean(item.getText()), 500)
                            : "";

                    String pubDateStr = DATE_FORMATTER.format(
                            Instant.ofEpochSecond(item.getTime()));

                    NewsMessage message = NewsMessage.builder()
                            .title(cleanTitle)
                            .link(item.getUrl())
                            .description(cleanDesc)
                            .source("Hacker News")
                            .type(NewsType.COMMUNITY)
                            .pubDateStr(pubDateStr)
                            // searchKeyword는 null (IT 뉴스 전체이므로 별도 카테고리 불필요)
                            .build();

                    kafkaTemplate.send("dev-news", message);
                    sentIdCache.add(storyId);
                    count++;

                    // API 부하 방지를 위한 딜레이
                    Thread.sleep(100);

                } catch (Exception e) {
                    log.warn(">>>> [HackerNewsProducer] 스토리 {} 조회 실패: {}", storyId, e.getMessage());
                }
            }

            if (count > 0) {
                log.info(">>>> [HackerNewsProducer] {}건의 새로운 스토리 전송 완료", count);
            } else {
                log.info(">>>> [HackerNewsProducer] 새로운 스토리가 없습니다.");
            }

            // 캐시 크기 관리 (최대 1000개 유지)
            if (sentIdCache.size() > 1000) {
                sentIdCache.clear();
                log.info(">>>> [HackerNewsProducer] 캐시 초기화");
            }

        } catch (Exception e) {
            log.error(">>>> [에러] Hacker News 수집 실패: {}", e.getMessage());
        }
    }
}
