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

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverNewsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate; // Kafka 전송 도구
    private final RestTemplate restTemplate = new RestTemplate(); // API 호출 도구

    @Value("${naver.api.client-id}")
    private String clientId;

    @Value("${naver.api.client-secret}")
    private String clientSecret;

    @Value("${naver.api.url}")
    private String apiUrl;

    // 10초마다 실행 (테스트용) -> 실제론 10분(600000) 정도로 늘리세요
    @Scheduled(fixedDelay = 10000)
    public void crawlNaverNews() {
        log.info(">>>> [NaverNewsProducer] 뉴스 수집 시작...");

        // 1. 검색어 설정 (IT 트렌드 키워드)
        String keyword = "Kafka";

        // 2. 요청 URI 만들기 (검색어, 정렬순, 개수)
        URI uri = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("query", keyword)
                .queryParam("display", 10)  // 10개씩 가져오기
                .queryParam("sort", "date") // 최신순
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        // 3. 헤더에 키 담아서 요청 보내기
        RequestEntity<Void> req = RequestEntity
                .get(uri)
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .build();

        try {
            ResponseEntity<NaverApiDto.Response> response = restTemplate.exchange(req, NaverApiDto.Response.class);

            // 4. 받아온 뉴스 목록을 Kafka로 쏘기
            if (response.getBody() != null && response.getBody().getItems() != null) {
                for (NaverApiDto.Item item : response.getBody().getItems()) {

                    // 메시지 박스에 담기
                    NewsMessage message = NewsMessage.builder()
                            .title(item.getTitle().replaceAll("<[^>]*>", "")) // HTML 태그 제거 (<b> 등)
                            .link(item.getOriginallink().isEmpty() ? item.getLink() : item.getOriginallink())
                            .description(item.getDescription().replaceAll("<[^>]*>", ""))
                            .source("Naver API")
                            .type(NewsType.NEWS) // "뉴스" 타입 지정
                            .pubDateStr(item.getPubDate())
                            .build();

                    // 🔥 Kafka로 발사! (토픽명: dev-news)
                    kafkaTemplate.send("dev-news", message);
                }
                log.info(">>>> [NaverNewsProducer] {}건의 뉴스 Kafka 전송 완료", response.getBody().getItems().size());
            }

        } catch (Exception e) {
            log.error(">>>> [에러] 네이버 뉴스 수집 실패: {}", e.getMessage());
        }
    }
}