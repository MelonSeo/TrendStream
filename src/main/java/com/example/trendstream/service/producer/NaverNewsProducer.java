package com.example.trendstream.service.producer;

import com.example.trendstream.domain.enums.NewsType;
import com.example.trendstream.dto.NaverApiDto;
import com.example.trendstream.dto.NewsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.example.trendstream.util.HtmlUtils;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverNewsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${naver.api.client-id}")
    private String clientId;

    @Value("${naver.api.client-secret}")
    private String clientSecret;

    @Value("${naver.api.url}")
    private String apiUrl;

    @Value("${naver.api.keywords}")
    private List<String> keywords;

    // 🔥 [중복 방지용 캐시] 이미 보낸 링크는 기억해둡니다.
    private final Set<String> sentLinkCache = Collections.synchronizedSet(new HashSet<>());

    // 앱 시작 60초 후 첫 실행, 이후 10분마다 실행 (Consumer 조인 시간 확보)
    @Scheduled(initialDelay = 60000, fixedDelay = 600000)
    public void crawlNaverNews() {
        log.info(">>>> [NaverNewsProducer] 전체 키워드에 대한 뉴스 수집을 시작합니다...");
        for (String keyword : keywords) {
            crawlAndSendNewsForKeyword(keyword);
        }
        log.info(">>>> [NaverNewsProducer] 전체 뉴스 수집을 완료했습니다.");
    }

    private void crawlAndSendNewsForKeyword(String keyword) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters()
                .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        restTemplate.getMessageConverters().stream()
                .filter(c -> c instanceof MappingJackson2HttpMessageConverter)
                .map(c -> (MappingJackson2HttpMessageConverter) c)
                .forEach(c -> c.setDefaultCharset(StandardCharsets.UTF_8));

        log.info(">>>> [NaverNewsProducer] '{}' 키워드 뉴스 수집 시작...", keyword);

        URI uri = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("query", keyword)
                .queryParam("display", 30)
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

                    if (sentLinkCache.contains(link)) {
                        continue;
                    }

                    String cleanTitle = HtmlUtils.clean(item.getTitle());
                    String cleanDesc = HtmlUtils.clean(item.getDescription());

                    NewsMessage message = NewsMessage.builder()
                            .title(cleanTitle)
                            .link(link)
                            .description(cleanDesc)
                            .source("Naver API")
                            .type(NewsType.NEWS)
                            .pubDateStr(item.getPubDate())
                            .searchKeyword(keyword)
                            .build();

                    kafkaTemplate.send("dev-news", message);
                    sentLinkCache.add(link);
                    count++;
                }

                if (count > 0) {
                    log.info(">>>> [NaverNewsProducer] '{}' 키워드로 {}건의 새로운 뉴스 전송 완료", keyword, count);
                } else {
                    log.info(">>>> [NaverNewsProducer] '{}' 키워드의 새로운 뉴스가 없습니다.", keyword);
                }
            }
        } catch (Exception e) {
            log.error(">>>> [에러] '{}' 키워드 뉴스 수집 실패: {}", keyword, e.getMessage());
        }
    }
}