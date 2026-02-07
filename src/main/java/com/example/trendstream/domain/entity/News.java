package com.example.trendstream.domain.entity;

import com.example.trendstream.domain.enums.NewsType;
import com.example.trendstream.domain.vo.AiResponse;
import io.hypersistence.utils.hibernate.type.json.JsonType; // ⭐️ 핵심!
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "news", indexes = {
        @Index(name = "idx_pub_date", columnList = "pubDate"),           // 최신순 정렬 최적화
        @Index(name = "idx_source", columnList = "source"),              // 출처별 검색 최적화
        @Index(name = "idx_search_keyword", columnList = "searchKeyword") // 카테고리별 검색 최적화
})
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true) // 링크가 같으면 중복 뉴스로 간주
    private String link;

    @Column(columnDefinition = "TEXT") // 본문이나 긴 요약은 TEXT 타입
    private String description;

    @Column(nullable = false)
    private String source; // NAVER, GEEK_NEWS 등

    @Enumerated(EnumType.STRING) // DB에 숫자가 아니라 "BLOG", "NEWS" 글자로 저장됨 (가독성 UP)
    @Column(nullable = false)
    private NewsType type;

    private String searchKeyword; // 검색에 사용된 키워드 (백엔드, AI, 클라우드 등)

    private LocalDateTime pubDate;

    // ⭐️ 여기가 면접 포인트! MySQL의 JSON 타입을 그대로 사용
    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private AiResponse aiResult;

    // 양방향 매핑 (NewsTag를 통해 태그 조회)
    @OneToMany(mappedBy = "news", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NewsTag> newsTags = new ArrayList<>();

    @Builder
    public News(String title, String link, String description, String source, NewsType type, LocalDateTime pubDate, AiResponse aiResult, String searchKeyword) {
        this.title = title;
        this.link = link;
        this.description = description;
        this.source = source;
        this.type = type;
        this.pubDate = pubDate;
        this.aiResult = aiResult;
        this.searchKeyword = searchKeyword;
    }

    // AI 분석 결과 업데이트 편의 메서드
    public void updateAiResult(AiResponse aiResult) {
        this.aiResult = aiResult;
    }
}