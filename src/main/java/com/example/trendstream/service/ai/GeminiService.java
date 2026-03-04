package com.example.trendstream.service.ai;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.domain.vo.AiResponse;
import com.example.trendstream.dto.GeminiInterfaceDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService implements AiAnalyzer {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String API_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=%s";

    @Override
    public List<AiResponse> analyzeBatchNews(List<News> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String url = String.format(API_URL_TEMPLATE, apiKey);

            // 1. 배치 프롬프트 생성
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
                4. score: 개발자에게 실질적으로 유익한 콘텐츠 품질 점수 (0~100)
                   - 80~100: 핵심 기술 발표, 오픈소스 릴리즈, 심층 기술 분석
                   - 50~79: 실용적인 튜토리얼, 기술 트렌드, 문제 해결 사례
                   - 20~49: 일반적인 IT 소식, 입문 수준의 내용
                   - 0~19: 아래에 해당하면 반드시 19 이하로 채점할 것
                     * 광고, 홍보, 제품/서비스 마케팅 성격의 글
                     * 중국어 등 외국어가 주를 이루는 글
                     * 비기술적 개인 일상, 의미없는 단순 수다
                     * 내용이 거의 없거나 제목만 있는 글

                [응답 형식] - 반드시 JSON 배열로 응답. 뉴스 순서대로 배열에 포함.
                [
                    {"summary": "...", "sentiment": "...", "keywords": [...], "score": ...},
                    {"summary": "...", "sentiment": "...", "keywords": [...], "score": ...}
                ]
                """, newsList.size(), newsListText.toString());

            // 2. API 요청
            GeminiInterfaceDto.Request requestDto = GeminiInterfaceDto.Request.of(prompt);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<GeminiInterfaceDto.Request> entity = new HttpEntity<>(requestDto, headers);

            log.info(">>>> [Gemini] 배치 분석 요청: {}개 뉴스", newsList.size());
            GeminiInterfaceDto.Response response = restTemplate.postForObject(url, entity, GeminiInterfaceDto.Response.class);

            // 3. 응답 파싱 (JSON 배열 → List<AiResponse>)
            if (response != null) {
                String jsonText = response.getText()
                        .replace("```json", "")
                        .replace("```", "")
                        .trim();

                List<AiResponse> results = objectMapper.readValue(
                        jsonText, new TypeReference<List<AiResponse>>() {});

                log.info(">>>> [Gemini] 배치 분석 완료: {}개 결과", results.size());
                return results;
            }

        } catch (Exception e) {
            log.error(">>>> [Gemini] 배치 분석 실패: {}", e.getMessage());
        }

        // 실패 시 기본값 반환
        List<AiResponse> fallback = new ArrayList<>();
        for (int i = 0; i < newsList.size(); i++) {
            fallback.add(new AiResponse("분석 실패", "NEUTRAL", null, 0));
        }
        return fallback;
    }

    /**
     * 단건 뉴스 분석 (레거시 호환용)
     */
    public AiResponse analyzeNews(String newsTitle, String newsDescription) {
        try {
            String url = String.format(API_URL_TEMPLATE, apiKey);

            String prompt = String.format("""
                너는 IT 뉴스 분석 전문가야. 아래 뉴스를 분석해서 반드시 JSON 형식으로만 답해줘.

                [뉴스 정보]
                제목: %s
                내용: %s

                [요구사항]
                1. summary: 내용을 3문장 이내로 요약 (한국어)
                2. sentiment: 분위기 (POSITIVE, NEGATIVE, NEUTRAL 중 택1)
                3. keywords: 핵심 키워드 3개 (리스트)
                4. score: 개발자에게 실질적으로 유익한 콘텐츠 품질 점수 (0~100)
                   - 80~100: 핵심 기술 발표, 오픈소스 릴리즈, 심층 기술 분석
                   - 50~79: 실용적인 튜토리얼, 기술 트렌드, 문제 해결 사례
                   - 20~49: 일반적인 IT 소식, 입문 수준의 내용
                   - 0~19: 광고/홍보, 외국어 주도, 비기술적 내용, 내용 없는 글

                [응답 예시]
                {
                    "summary": "자바 21버전이 출시되었습니다.",
                    "sentiment": "POSITIVE",
                    "keywords": ["Java", "JDK", "LTS"],
                    "score": 90
                }
                """, newsTitle, newsDescription);

            GeminiInterfaceDto.Request requestDto = GeminiInterfaceDto.Request.of(prompt);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<GeminiInterfaceDto.Request> entity = new HttpEntity<>(requestDto, headers);

            GeminiInterfaceDto.Response response = restTemplate.postForObject(url, entity, GeminiInterfaceDto.Response.class);

            if (response != null) {
                String jsonText = response.getText()
                        .replace("```json", "")
                        .replace("```", "")
                        .trim();

                return objectMapper.readValue(jsonText, AiResponse.class);
            }

        } catch (Exception e) {
            log.error(">>>> [Gemini] 분석 실패: {}", e.getMessage());
        }

        return new AiResponse("분석 실패", "NEUTRAL", null, 0);
    }
}