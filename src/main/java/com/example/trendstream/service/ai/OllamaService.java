package com.example.trendstream.service.ai;

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
 * Ollama 로컬 LLM 분석 서비스
 *
 * [특징]
 * - 로컬에서 실행되므로 API 한도 제한 없음
 * - GeminiService와 동일한 프롬프트 사용
 * - Ollama REST API (POST /api/generate) 호출
 */
@Slf4j
@RequiredArgsConstructor
public class OllamaService implements AiAnalyzer {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ollama.api.url:http://localhost:11434}")
    private String ollamaApiUrl;

    @Value("${ollama.model:gemma3:4b}")
    private String model;

    @Override
    public List<AiResponse> analyzeBatchNews(List<News> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String url = ollamaApiUrl + "/api/generate";

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

            // 2. Ollama API 요청
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "format", "json"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info(">>>> [Ollama] 배치 분석 요청: {}개 뉴스, 모델: {}", newsList.size(), model);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            // 3. 응답 파싱
            if (response != null && response.containsKey("response")) {
                String jsonText = ((String) response.get("response"))
                        .replace("```json", "")
                        .replace("```", "")
                        .trim();

                List<AiResponse> results = objectMapper.readValue(
                        jsonText, new TypeReference<List<AiResponse>>() {});

                log.info(">>>> [Ollama] 배치 분석 완료: {}개 결과", results.size());
                return results;
            }

        } catch (Exception e) {
            log.error(">>>> [Ollama] 배치 분석 실패: {}", e.getMessage());
        }

        // 실패 시 기본값 반환
        List<AiResponse> fallback = new ArrayList<>();
        for (int i = 0; i < newsList.size(); i++) {
            fallback.add(new AiResponse("분석 실패", "NEUTRAL", null, 0));
        }
        return fallback;
    }
}
