package com.example.trendstream.dto;

import com.example.trendstream.domain.enums.NewsType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsMessage { //수집기(Producer)가 Kafka에 던질 "택배 상자"입니다.
    private String title;
    private String link;
    private String description;
    private String source;     // 출처 (예: Naver API)
    private NewsType type;     // 분류 (NEWS, BLOG, COMMUNITY)
    private String pubDateStr; // 날짜는 문자열로 일단 보냄
}