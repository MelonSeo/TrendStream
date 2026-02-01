package com.example.trendstream.service;

import com.example.trendstream.domain.enums.NewsType;
import com.example.trendstream.dto.NaverApiDto;
import com.example.trendstream.dto.NewsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverNewsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${naver.api.client-id}")
    private String clientId;

    @Value("${naver.api.client-secret}")
    private String clientSecret;

    @Value("${naver.api.url}")
    private String apiUrl;

    // 🔥 [중복 방지용 캐시] 이미 보낸 링크는 기억해둡니다.
    private final Set<String> sentLinkCache = Collections.synchronizedSet(new HashSet<>());

    // 10초마다 실행 (테스트 끝나면 시간을 늘리세요)
    @Scheduled(fixedDelay = 300000)
    public void crawlNaverNews() {
        log.info(">>>> [NaverNewsProducer] 뉴스 수집 시작...");

        String keyword = "IT 기술";

        URI uri = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("query", keyword)
                .queryParam("display", 10)
                .queryParam("sort", "date") // 최신순
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        RequestEntity<Void> req = RequestEntity
                .get(uri)
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .build();

        try {
            ResponseEntity<NaverApiDto.Response> response = restTemplate.exchange(req, NaverApiDto.Response.class);

            if (response.getBody() != null && response.getBody().getItems() != null) {
                int count = 0;
                for (NaverApiDto.Item item : response.getBody().getItems()) {

                    String link = item.getOriginallink().isEmpty() ? item.getLink() : item.getOriginallink();

                    // 🔥 [중복 체크] 이미 보낸 링크면 건너뜀
                    if (sentLinkCache.contains(link)) {
                        continue;
                    }

                    NewsMessage message = NewsMessage.builder()
                            .title(item.getTitle().replaceAll("<[^>]*>", ""))
                            .link(link)
                            .description(item.getDescription().replaceAll("<[^>]*>", ""))
                            .source("Naver API")        // 필드: source
                            .type(NewsType.NEWS)        // 필드: type (Enum)
                            .pubDateStr(item.getPubDate()) // 필드: pubDateStr (주의: String 타입)
                            .build();

                    // Kafka 전송
                    kafkaTemplate.send("dev-news", message);

                    // 캐시 저장
                    sentLinkCache.add(link);
                    count++;
                }

                if (count > 0) {
                    log.info(">>>> [NaverNewsProducer] {}건의 새로운 뉴스 전송 완료", count);
                } else {
                    log.info(">>>> [NaverNewsProducer] 새로운 뉴스가 없습니다 (중복 제외됨).");
                }
            }

        } catch (Exception e) {
            log.error(">>>> [에러] 네이버 뉴스 수집 실패: {}", e.getMessage());
        }
    }
}