package com.example.trendstream.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "news_tags")
public class NewsTag {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // 지연 로딩 필수 (성능 최적화)
    @JoinColumn(name = "news_id")
    private News news;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    // 생성자: 만들면서 바로 양쪽 연결
    public NewsTag(News news, Tag tag) {
        this.news = news;
        this.tag = tag;
        news.getNewsTags().add(this);
    }
}