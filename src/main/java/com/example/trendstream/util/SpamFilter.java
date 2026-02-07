package com.example.trendstream.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 스팸 필터 유틸리티
 *
 * [필터링 대상]
 * - 도박/카지노 광고
 * - 불법 서비스 홍보
 * - 의심스러운 연락처 패턴 (QQ, Telegram 등)
 *
 * [적용 위치]
 * - Producer: 스팸 뉴스는 Kafka로 전송하지 않음
 * - Consumer: 추가 필터링 (이중 방어)
 */
public class SpamFilter {

    private SpamFilter() {
        // 유틸리티 클래스
    }

    /** 스팸 키워드 (도박, 불법 서비스) */
    private static final List<String> SPAM_KEYWORDS = List.of(
            // 도박 관련
            "카지노", "바카라", "슬롯", "도박", "배팅", "베팅",
            "casino", "baccarat", "gambling", "betting",
            "娱乐", "赌场", "百家乐", "龙虎", "牛牛",

            // 불법 서비스
            "토토", "먹튀", "꽁머니", "충전", "환전",

            // 성인/불법
            "성인", "야동", "porn", "sex",

            // 금융 사기
            "대출", "급전", "일수", "월변"
    );

    /** 의심스러운 패턴 (정규식) */
    private static final List<Pattern> SPAM_PATTERNS = List.of(
            // QQ 번호 패턴 (QQ: 숫자)
            Pattern.compile("QQ\\s*[:：]?\\s*\\d{5,}", Pattern.CASE_INSENSITIVE),

            // Telegram 패턴 (@username 또는 TG:)
            Pattern.compile("(TG|Telegram|텔레그램)\\s*[:：]?\\s*@?\\w+", Pattern.CASE_INSENSITIVE),

            // 위챗 패턴
            Pattern.compile("(WeChat|微信|위챗)\\s*[:：]?\\s*\\w+", Pattern.CASE_INSENSITIVE),

            // 의심스러운 도메인 패턴 (숫자로만 된 도메인)
            Pattern.compile("\\d{5,}\\.com"),

            // 상하분 (上下分) - 도박 용어
            Pattern.compile("上下分|상하분")
    );

    /**
     * 스팸 여부 판단
     *
     * @param title 제목
     * @param description 설명
     * @return true면 스팸
     */
    public static boolean isSpam(String title, String description) {
        String combined = ((title != null ? title : "") + " " +
                          (description != null ? description : "")).toLowerCase();

        // 1. 키워드 체크
        for (String keyword : SPAM_KEYWORDS) {
            if (combined.contains(keyword.toLowerCase())) {
                return true;
            }
        }

        // 2. 패턴 체크
        for (Pattern pattern : SPAM_PATTERNS) {
            if (pattern.matcher(combined).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 스팸 이유 반환 (디버깅용)
     */
    public static String getSpamReason(String title, String description) {
        String combined = ((title != null ? title : "") + " " +
                          (description != null ? description : "")).toLowerCase();

        for (String keyword : SPAM_KEYWORDS) {
            if (combined.contains(keyword.toLowerCase())) {
                return "키워드: " + keyword;
            }
        }

        for (Pattern pattern : SPAM_PATTERNS) {
            if (pattern.matcher(combined).find()) {
                return "패턴: " + pattern.pattern();
            }
        }

        return null;
    }
}
