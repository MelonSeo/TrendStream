package com.example.trendstream.service.ai;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.domain.vo.AiResponse;

import java.util.List;

/**
 * AI 분석 공통 인터페이스
 *
 * 구현체: GeminiService (외부 API), OllamaService (로컬 LLM)
 * AiConfig에서 ai.provider 설정값에 따라 빈 선택
 */
public interface AiAnalyzer {
    List<AiResponse> analyzeBatchNews(List<News> newsList);
}
