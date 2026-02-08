package com.example.trendstream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 트렌드 키워드 응답 DTO
 *
 * [구조]
 * - keyword: 트렌드 키워드 (소문자 정규화된 태그 이름)
 * - count: 해당 기간 내 키워드 등장 횟수
 * - relatedNews: 관련 뉴스 상위 3개 요약 정보
 *
 * [@NoArgsConstructor 필요 이유]
 * - Redis @Cacheable 사용 시 Jackson이 역직렬화할 때 기본 생성자 필요
 * - @Builder만 있으면 기본 생성자가 없어서 역직렬화 실패
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendResponseDto {

    private String keyword;
    private Long count;
    private List<NewsSummary> relatedNews;

    /**
     * 관련 뉴스 요약 (트렌드 응답 내부용)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewsSummary {
        private Long id;
        private String title;
        private String link;
    }
}
