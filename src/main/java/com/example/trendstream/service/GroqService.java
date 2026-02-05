package com.example.trendstream.service;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.domain.vo.AiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Groq 클라우드 LLM 분석 서비스
 *
 * [특징]
 * - OpenAI 호환 API (chat/completions 엔드포인트)
 * - 무료 티어: llama-3.1-8b-instant 기준 RPM 30, RPD 14,400
 * - 응답 속도가 매우 빠름 (LPU 추론 엔진)
 */
@Slf4j
@RequiredArgsConstructor
public class GroqService implements AiAnalyzer {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String model;

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Override
    public List<AiResponse> analyzeBatchNews(List<News> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 1. 배치 프롬프트 생성 (GeminiService와 동일 형식)
            StringBuilder newsListText = new StringBuilder();
            for (int i = 0; i < newsList.size(); i++) {
                News news = newsList.get(i);
                newsListText.append(String.format("""
                    [뉴스 %d]
                    제목: %s
                    내용: %s

                    """, i + 1, news.getTitle(), news.getDescription()));
            }

            String prompt = String.format("""
                너는 IT 뉴스 분석 전문가야. 아래 %d개의 뉴스를 각각 분석해서 반드시 JSON 배열 형식으로만 답해줘.

                %s
                [요구사항]
                각 뉴스에 대해:
                1. summary: 내용을 3문장 이내로 요약 (한국어)
                2. sentiment: 분위기 (POSITIVE, NEGATIVE, NEUTRAL 중 택1)
                3. keywords: 핵심 키워드 3개 (리스트)
                4. score: 개발자에게 중요한 정도 (0~100점)

                [응답 형식] - 반드시 JSON 배열로만 응답. 다른 텍스트 없이 JSON만 출력.
                [
                    {"summary": "...", "sentiment": "...", "keywords": [...], "score": ...},
                    {"summary": "...", "sentiment": "...", "keywords": [...], "score": ...}
                ]
                """, newsList.size(), newsListText.toString());

            // 2. Groq API 요청 (OpenAI 호환 형식)
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.3
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info(">>>> [Groq] 배치 분석 요청: {}개 뉴스, 모델: {}", newsList.size(), model);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(API_URL, entity, Map.class);

            // 3. 응답 파싱 (OpenAI 형식: choices[0].message.content)
            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");

                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");

                    String jsonText = content
                            .replace("```json", "")
                            .replace("```", "")
                            .trim();

                    List<AiResponse> results = objectMapper.readValue(
                            jsonText, new TypeReference<List<AiResponse>>() {});

                    log.info(">>>> [Groq] 배치 분석 완료: {}개 결과", results.size());
                    return results;
                }
            }

        } catch (Exception e) {
            log.error(">>>> [Groq] 배치 분석 실패: {}", e.getMessage());
        }

        // 실패 시 기본값 반환
        List<AiResponse> fallback = new ArrayList<>();
        for (int i = 0; i < newsList.size(); i++) {
            fallback.add(new AiResponse("분석 실패", "NEUTRAL", null, 0));
        }
        return fallback;
    }
}
