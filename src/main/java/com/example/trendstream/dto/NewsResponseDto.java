package com.example.trendstream.dto;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.domain.enums.NewsType;
import com.example.trendstream.domain.vo.AiResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;


@Getter
@Builder
public class NewsResponseDto {

    private Long id;                  // 뉴스 고유 식별자
    private String title;             // 뉴스 제목
    private String link;              // 원본 기사 URL
    private String description;       // 뉴스 요약/본문
    private String source;            // 출처 (NAVER, GEEK_NEWS 등)
    private NewsType type;            // 뉴스 타입 (NEWS, BLOG, COMMUNITY)
    private LocalDateTime pubDate;    // 발행일시
    private AiResponse aiResult;      // AI 분석 결과 (요약, 감정, 키워드, 점수)
    private List<String> tags;        // 태그 목록 (예: ["Spring", "Kafka"])
    private String searchKeyword;     // 검색에 사용된 키워드 (백엔드, AI, 클라우드 등)


    public static NewsResponseDto from(News news) {
        // NewsTag 연관관계를 통해 태그 이름만 추출
        // Stream API를 사용하여 함수형 프로그래밍 스타일로 변환
        List<String> tagNames = news.getNewsTags().stream()
                .map(newsTag -> newsTag.getTag().getName())  // NewsTag -> Tag -> name 추출
                .toList();  // Java 16+ 불변 리스트로 수집

        return NewsResponseDto.builder()
                .id(news.getId())
                .title(news.getTitle())
                .link(news.getLink())
                .description(news.getDescription())
                .source(news.getSource())
                .type(news.getType())
                .pubDate(news.getPubDate())
                .aiResult(news.getAiResult())
                .tags(tagNames)
                .searchKeyword(news.getSearchKeyword())
                .build();
    }
}
