package com.example.trendstream.service.consumer;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.domain.enums.NewsType;
import com.example.trendstream.repository.NewsRepository;
import com.example.trendstream.dto.NewsMessage;
import com.example.trendstream.util.SpamFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;


@Slf4j
@Service
@RequiredArgsConstructor
public class NewsConsumer {

    private final NewsRepository newsRepository;

    private static final DateTimeFormatter NAVER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    @KafkaListener(topics = "dev-news", groupId = "news-group")
    @Transactional
    public void consumeNews(NewsMessage message) {
        log.info(">>>> [Consumer] 뉴스 수신: {}", message.getTitle());

        // 1. 스팸 필터링 (소스 무관 2차 방어선)
        if (SpamFilter.isSpam(message.getTitle(), message.getDescription())) {
            log.info(">>>> [Spam] 스팸 필터링: {} - 이유: {}",
                    message.getTitle(),
                    SpamFilter.getSpamReason(message.getTitle(), message.getDescription()));
            return;
        }

        // 2. 이미 DB에 있는 뉴스인지 확인 (중복 방지)
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
                .source(message.getSource())  // Producer가 보낸 소스 사용
                .type(message.getType())      // Producer가 보낸 타입 사용
                .pubDate(publishedAt)
                .aiResult(null)  // AI 분석은 스케줄러가 배치로 처리
                .searchKeyword(message.getSearchKeyword())
                .build();

        newsRepository.save(news);
        log.info(">>>> [DB 저장 완료] ID: {} (AI 분석 대기중)", news.getId());
    }
}