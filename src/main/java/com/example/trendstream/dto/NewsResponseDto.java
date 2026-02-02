package com.example.trendstream.dto;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.domain.enums.NewsType;
import com.example.trendstream.domain.vo.AiResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 뉴스 응답 DTO (Data Transfer Object)
 *
 * [왜 DTO를 사용하는가?]
 * 1. Entity 직접 반환 시 순환 참조 문제 발생 가능 (News -> NewsTag -> News ...)
 * 2. 클라이언트에게 필요한 데이터만 선별하여 전송 (보안, 성능)
 * 3. API 스펙과 DB 스키마를 분리하여 유연성 확보
 *
 * [Builder 패턴 사용 이유]
 * - 필드가 많을 때 가독성 있는 객체 생성
 * - 불변 객체 생성에 적합
 */
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

    /**
     * Entity -> DTO 변환 정적 팩토리 메서드
     *
     * [정적 팩토리 메서드 장점]
     * 1. 이름을 가질 수 있어 의도가 명확함 (from, of, valueOf 등)
     * 2. 호출할 때마다 새 객체를 생성할 필요 없음 (캐싱 가능)
     * 3. 반환 타입의 하위 타입 객체 반환 가능
     *
     * @param news 변환할 News 엔티티 (Fetch Join으로 태그까지 로딩된 상태)
     * @return 클라이언트 응답용 DTO
     */
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
                .build();
    }
}
