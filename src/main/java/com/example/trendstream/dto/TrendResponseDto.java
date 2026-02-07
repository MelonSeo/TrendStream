package com.example.trendstream.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 트렌드 키워드 응답 DTO
 *
 * [구조]
 * - keyword: 트렌드 키워드 (소문자 정규화된 태그 이름)
 * - count: 해당 기간 내 키워드 등장 횟수
 * - relatedNews: 관련 뉴스 상위 3개 요약 정보
 */
@Getter
@Builder
public class TrendResponseDto {

    private String keyword;
    private Long count;
    private List<NewsSummary> relatedNews;

    /**
     * 관련 뉴스 요약 (트렌드 응답 내부용)
     */
    @Getter
    @Builder
    public static class NewsSummary {
        private Long id;
        private String title;
        private String link;
    }
}
