package com.example.trendstream.service;

import com.example.trendstream.domain.vo.AiResponse;
import com.example.trendstream.dto.GeminiInterfaceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final ObjectMapper objectMapper; // JSON 변환기 (Spring이 줌)
    private final RestTemplate restTemplate = new RestTemplate(); // HTTP 요청 도구

    @Value("${gemini.api.key}")
    private String apiKey;

    // Gemini Flash 모델 (싸고 빠름)
    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=%s";

    public AiResponse analyzeNews(String newsTitle, String newsDescription) {
        try {
            String url = String.format(API_URL_TEMPLATE, apiKey);

            // 1. 프롬프트 작성 (AI에게 역할을 부여하고, JSON 응답을 강제함)
            String prompt = String.format("""
                너는 IT 뉴스 분석 전문가야. 아래 뉴스를 분석해서 반드시 JSON 형식으로만 답해줘.
                
                [뉴스 정보]
                제목: %s
                내용: %s
                
                [요구사항]
                1. summary: 내용을 3문장 이내로 요약 (한국어)
                2. sentiment: 분위기 (POSITIVE, NEGATIVE, NEUTRAL 중 택1)
                3. keywords: 핵심 키워드 3개 (리스트)
                4. score: 개발자에게 중요한 정도 (0~100점)
                
                [응답 예시]
                {
                    "summary": "자바 21버전이 출시되었습니다.",
                    "sentiment": "POSITIVE",
                    "keywords": ["Java", "JDK", "LTS"],
                    "score": 90
                }
                """, newsTitle, newsDescription);

            // 2. 요청 보내기
            GeminiInterfaceDto.Request requestDto = GeminiInterfaceDto.Request.of(prompt);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<GeminiInterfaceDto.Request> entity = new HttpEntity<>(requestDto, headers);

            GeminiInterfaceDto.Response response = restTemplate.postForObject(url, entity, GeminiInterfaceDto.Response.class);

            // 3. 응답 파싱 (String -> JSON -> AiResponse 객체)
            if (response != null) {
                String jsonText = response.getText()
                        .replace("```json", "") // 마크다운 문법 제거
                        .replace("```", "")
                        .trim();

                return objectMapper.readValue(jsonText, AiResponse.class);
            }

        } catch (Exception e) {
            log.error(">>>> [Gemini] 분석 실패: {}", e.getMessage());
        }

        // 실패 시 빈 객체 반환 (에러 안 나게)
        return new AiResponse("분석 실패", "NEUTRAL", null, 0);
    }
}