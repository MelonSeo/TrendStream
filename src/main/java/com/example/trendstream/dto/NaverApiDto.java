package com.example.trendstream.dto;

import lombok.Data;
import java.util.List;

public class NaverApiDto {

    @Data
    public static class Response {
        private String lastBuildDate;
        private int total;
        private int start;
        private int display;
        private List<Item> items; // 뉴스 기사 리스트
    }

    // 실제 기사 하나하나의 정보
    @Data
    public static class Item {
        private String title;         // 기사 제목 (HTML 태그 포함됨)
        private String originallink;  // 오리지널 링크
        private String link;          // 네이버 뉴스 링크
        private String description;   // 요약문
        private String pubDate;       // 발행일
    }
}