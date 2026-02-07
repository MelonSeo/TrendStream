package com.example.trendstream.util;

/**
 * HTML 정제 유틸리티
 *
 * [처리 항목]
 * 1. HTML 태그 제거: <p>, <b>, <a href="..."> 등
 * 2. HTML 엔티티 디코딩: &amp; → &, &quot; → ", &#38; → & 등
 * 3. 공백 정규화: 연속 공백, 줄바꿈 → 단일 공백
 */
public class HtmlUtils {

    private HtmlUtils() {
        // 유틸리티 클래스
    }

    /**
     * HTML 태그 제거 + 엔티티 디코딩 + 공백 정규화
     */
    public static String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return decodeEntities(removeTags(text)).trim();
    }

    /**
     * HTML 태그 제거
     * <p>내용</p> → 내용
     */
    public static String removeTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "");
    }

    /**
     * HTML 엔티티 디코딩
     *
     * [지원 엔티티]
     * - Named: &amp; &lt; &gt; &quot; &apos; &nbsp;
     * - Numeric: &#38; &#60; &#x26; 등
     */
    public static String decodeEntities(String text) {
        if (text == null) return "";

        String result = text
                // Named entities (흔한 것들)
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                // 추가 엔티티
                .replace("&middot;", "·")
                .replace("&hellip;", "...")
                .replace("&ndash;", "–")
                .replace("&mdash;", "—")
                .replace("&lsquo;", "'")
                .replace("&rsquo;", "'")
                .replace("&ldquo;", "\"")
                .replace("&rdquo;", "\"");

        // Numeric entities: &#38; &#60; 등
        result = decodeNumericEntities(result);

        // Hex entities: &#x26; &#x3C; 등
        result = decodeHexEntities(result);

        // 연속 공백 정규화
        result = result.replaceAll("\\s+", " ");

        return result;
    }

    /**
     * Numeric entities 디코딩: &#38; → &
     */
    private static String decodeNumericEntities(String text) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '&' && i + 2 < text.length() && text.charAt(i + 1) == '#') {
                int semicolon = text.indexOf(';', i + 2);
                if (semicolon != -1 && semicolon - i < 8) {
                    String numStr = text.substring(i + 2, semicolon);
                    try {
                        if (!numStr.isEmpty() && Character.isDigit(numStr.charAt(0))) {
                            int codePoint = Integer.parseInt(numStr);
                            result.append((char) codePoint);
                            i = semicolon + 1;
                            continue;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            result.append(text.charAt(i));
            i++;
        }
        return result.toString();
    }

    /**
     * Hex entities 디코딩: &#x26; → &
     */
    private static String decodeHexEntities(String text) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '&' && i + 3 < text.length()
                    && text.charAt(i + 1) == '#'
                    && (text.charAt(i + 2) == 'x' || text.charAt(i + 2) == 'X')) {
                int semicolon = text.indexOf(';', i + 3);
                if (semicolon != -1 && semicolon - i < 10) {
                    String hexStr = text.substring(i + 3, semicolon);
                    try {
                        int codePoint = Integer.parseInt(hexStr, 16);
                        result.append((char) codePoint);
                        i = semicolon + 1;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            result.append(text.charAt(i));
            i++;
        }
        return result.toString();
    }

    /**
     * 텍스트 길이 제한 (말줄임 추가)
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
