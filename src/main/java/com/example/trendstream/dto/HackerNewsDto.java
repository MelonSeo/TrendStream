package com.example.trendstream.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Hacker News API 응답 DTO
 *
 * API: https://hacker-news.firebaseio.com/v0/item/{id}.json
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HackerNewsDto {
    private Long id;
    private String type;      // story, comment, job, poll, pollopt
    private String by;        // 작성자
    private Long time;        // Unix timestamp
    private String title;     // 제목
    private String url;       // 원본 링크 (없을 수도 있음 - Ask HN 등)
    private Integer score;    // 점수
    private Integer descendants; // 댓글 수
    private String text;      // 본문 (Ask HN 등에서 사용)
}
