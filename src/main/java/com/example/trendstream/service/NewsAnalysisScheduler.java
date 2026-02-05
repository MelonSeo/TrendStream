package com.example.trendstream.service;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.domain.vo.AiResponse;
import com.example.trendstream.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 뉴스 AI 분석 스케줄러 (배치 처리)
 *
 * [도입 배경]
 * - 기존: 뉴스 1개당 API 1회 호출 → 무료 한도 빠르게 소진
 * - 개선: 3개씩 묶어서 1회 호출 → API 호출 절약
 *
 * [동작 방식]
 * 1. 주기적으로 AI 분석 안 된 뉴스 조회 (aiResult = null)
 * 2. 최대 3개씩 배치로 Gemini API 호출
 * 3. 분석 결과를 각 뉴스에 업데이트
 *
 * [실행 주기]
 * - 30초마다 실행 (Gemini 무료 티어 Rate Limit 고려)
 * - 한 번에 최대 3개 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsAnalysisScheduler {

    private final NewsRepository newsRepository;
    private final GeminiService geminiService;

    /** 배치 크기: 한 번에 분석할 뉴스 개수 */
    private static final int BATCH_SIZE = 3;

    /**
     * AI 분석 배치 작업 (30초마다 실행)
     *
     * [Rate Limit 고려]
     * - Gemini 무료 티어: 분당 15회 제한
     * - 30초 간격 + 3개 배치 = 분당 2회 호출 (여유 있음)
     *
     * [처리 흐름]
     * 1. aiResult가 null인 뉴스 3개 조회
     * 2. 없으면 스킵
     * 3. Gemini 배치 분석 호출
     * 4. 각 뉴스에 결과 업데이트
     */
    @Scheduled(fixedDelay = 30000)  // 30초마다 실행 (이전 작업 완료 후 30초 대기)
    @Transactional
    public void analyzeUnprocessedNews() {
        // 1. AI 분석 안 된 뉴스 조회
        Page<News> unprocessedPage = newsRepository.findByAiResultIsNull(
                PageRequest.of(0, BATCH_SIZE));

        List<News> unprocessedNews = unprocessedPage.getContent();

        if (unprocessedNews.isEmpty()) {
            log.debug(">>>> [Scheduler] 분석 대기 중인 뉴스 없음");
            return;
        }

        log.info(">>>> [Scheduler] 배치 분석 시작: {}개 뉴스", unprocessedNews.size());

        // 2. 배치 분석 요청
        List<AiResponse> results = geminiService.analyzeBatchNews(unprocessedNews);

        // 3. 결과 매핑 및 업데이트
        for (int i = 0; i < unprocessedNews.size() && i < results.size(); i++) {
            News news = unprocessedNews.get(i);
            AiResponse aiResult = results.get(i);

            news.updateAiResult(aiResult);
            log.info(">>>> [분석 완료] ID: {}, 제목: {}, 점수: {}",
                    news.getId(),
                    news.getTitle().substring(0, Math.min(20, news.getTitle().length())) + "...",
                    aiResult.getScore());
        }

        // 4. 변경사항 저장 (Dirty Checking으로 자동 저장되지만 명시적으로 호출)
        newsRepository.saveAll(unprocessedNews);

        log.info(">>>> [Scheduler] 배치 분석 완료: {}개 처리됨", unprocessedNews.size());
    }
}
