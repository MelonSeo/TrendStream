package com.example.trendstream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;


@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendResponseDto {

    private String keyword;
    private Long count;
    private List<NewsSummary> relatedNews;


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
