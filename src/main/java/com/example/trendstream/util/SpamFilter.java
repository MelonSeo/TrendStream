package com.example.trendstream.util;

import java.util.List;
import java.util.regex.Pattern;

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
            "대출", "급전", "일수", "월변",

            // 계정/서비스 구매 광고
            "계정 구매", "계정구매", "구매하기", "지금 구매",
            "buy account", "buy followers", "buy views", "buy likes",
            "cheap followers", "cheap accounts", "get followers",
            "accounts for sale", "account for sale"
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

    /** 한자(CJK) 비율이 이 값을 초과하면 중국어 광고글로 판단 */
    private static final double MAX_CHINESE_CHAR_RATIO = 0.25;

    /** 베트남어 특유 문자 패턴 (Latin Extended Additional 블록, U+1E00~U+1EFF) */
    private static final Pattern VIETNAMESE_PATTERN =
            Pattern.compile("[\\x{1E00}-\\x{1EFF}]");

    /** 베트남어 비율이 이 값을 초과하면 베트남어 글로 판단 */
    private static final double MAX_VIETNAMESE_CHAR_RATIO = 0.05;

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

        // 3. 한자 비율 체크 (중국어 광고글 차단)
        if (hasExcessiveChineseChars(title, description)) {
            return true;
        }

        // 4. 베트남어 비율 체크
        if (hasExcessiveVietnameseChars(title, description)) {
            return true;
        }

        return false;
    }

    /**
     * CJK 통합 한자(U+4E00~U+9FFF) 비율이 임계값을 초과하는지 확인
     * 한국어 서비스이므로 제목+내용의 25% 이상이 한자면 중국어 광고글로 판단
     */
    public static boolean hasExcessiveChineseChars(String title, String description) {
        String combined = (title != null ? title : "") + (description != null ? description : "");
        if (combined.isEmpty()) {
            return false;
        }

        long chineseCount = combined.chars()
                .filter(c -> c >= 0x4E00 && c <= 0x9FFF)
                .count();

        return (double) chineseCount / combined.length() > MAX_CHINESE_CHAR_RATIO;
    }

    /**
     * 베트남어 특유 문자(U+1E00~U+1EFF) 비율이 임계값을 초과하는지 확인
     * 한국어 서비스이므로 5% 이상이면 베트남어 글로 판단
     */
    public static boolean hasExcessiveVietnameseChars(String title, String description) {
        String combined = (title != null ? title : "") + (description != null ? description : "");
        if (combined.isEmpty()) {
            return false;
        }

        long vietnameseCount = combined.chars()
                .filter(c -> c >= 0x1E00 && c <= 0x1EFF)
                .count();

        return (double) vietnameseCount / combined.length() > MAX_VIETNAMESE_CHAR_RATIO;
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

        if (hasExcessiveChineseChars(title, description)) {
            return "한자 비율 초과";
        }

        if (hasExcessiveVietnameseChars(title, description)) {
            return "베트남어 비율 초과";
        }

        return null;
    }
}
