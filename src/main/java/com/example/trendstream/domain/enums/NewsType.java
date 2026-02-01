package com.example.trendstream.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NewsType {
    NEWS("언론사 뉴스"),       // 네이버 뉴스, IT 월드 등
    BLOG("기술 블로그"),       // 우아한형제들, 토스, 당근 등
    COMMUNITY("커뮤니티");     // 긱뉴스, 레딧, 카페

    private final String description; // 설명 (나중에 화면에 뿌릴 때 좋음)
}