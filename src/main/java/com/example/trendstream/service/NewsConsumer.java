package com.example.trendstream.service;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.domain.enums.NewsType;
import com.example.trendstream.repository.NewsRepository;
import com.example.trendstream.domain.vo.AiResponse;
import com.example.trendstream.dto.NewsMessage;
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

    private final GeminiService geminiService;
    private final NewsRepository newsRepository;

    private static final DateTimeFormatter NAVER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    // Kafka에서 'dev-news' 토픽을 감시하다가 메시지가 오면 이 메서드가 실행됨
    @KafkaListener(topics = "dev-news", groupId = "news-group")
    @Transactional
    public void consumeNews(NewsMessage message) {
        log.info(">>>> [Consumer] 뉴스 수신: {}", message.getTitle());

        // 1. 이미 DB에 있는 뉴스인지 확인 (중복 방지)
        if (newsRepository.existsByLink(message.getLink())) {
            log.info(">>>> [Skip] 이미 존재하는 뉴스입니다.");
            return;
        }

        LocalDateTime publishedAt;
        try {
            // NewsMessage의 pubDate(String)를 꺼내서 LocalDateTime으로 변환
            publishedAt = LocalDateTime.parse(message.getPubDateStr(), NAVER_DATE_FORMAT);
        } catch (Exception e) {
            log.warn("날짜 파싱 실패, 현재 시간으로 대체: {}", message.getPubDateStr());
            publishedAt = LocalDateTime.now();
        }

        // 2. Gemini에게 분석 요청 (AI야, 이거 읽고 요약해줘!)
        AiResponse aiResult = geminiService.analyzeNews(message.getTitle(), message.getDescription());
        log.info(">>>> [AI 분석 완료] 점수: {}점, 감정: {}", aiResult.getScore(), aiResult.getSentiment());

        // 3. DB에 저장할 엔티티(News)로 변환
        News news = News.builder()
                .title(message.getTitle())
                .link(message.getLink())
                .description(message.getDescription())
                .source("Naver Open API")
                .type(NewsType.NEWS) // ✅ Enum 설정 (이게 없으면 에러 납니다!)
                .pubDate(publishedAt)
                .aiResult(aiResult)  // ✅ 이제 여기서 에러 안 남!
                .build();

        // 4. 저장
        newsRepository.save(news);
        log.info(">>>> [DB 저장 완료] ID: {}", news.getId());
    }
}