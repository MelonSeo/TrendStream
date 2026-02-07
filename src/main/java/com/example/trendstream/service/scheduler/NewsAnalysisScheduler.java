package com.example.trendstream.service.scheduler;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.domain.entity.NewsTag;
import com.example.trendstream.domain.entity.Tag;
import com.example.trendstream.domain.vo.AiResponse;
import com.example.trendstream.repository.NewsRepository;
import com.example.trendstream.service.ai.AiAnalyzer;
import com.example.trendstream.repository.NewsTagRepository;
import com.example.trendstream.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 뉴스 AI 분석 스케줄러 (배치 처리)
 *
 * [동작 방식]
 * 1. 미분석 뉴스 조회 (aiResult = null) → 배치 분석
 * 2. 분석 실패 뉴스 조회 (summary = '분석 실패') → 재분석
 *
 * [실행 주기]
 * - ai.analysis.interval 프로퍼티로 설정 (기본 10초)
 * - Ollama(로컬): Rate Limit 없으므로 짧게 설정 가능
 * - Gemini(외부): 30초 이상 권장 (분당 15회 제한)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsAnalysisScheduler {

    private final NewsRepository newsRepository;
    private final AiAnalyzer aiAnalyzer;
    private final TagRepository tagRepository;
    private final NewsTagRepository newsTagRepository;

    /** 배치 크기: 한 번에 분석할 뉴스 개수 */
    private static final int BATCH_SIZE = 3;

    /**
     * AI 분석 배치 작업 (설정 간격마다 실행)
     *
     * [처리 우선순위]
     * 1순위: aiResult가 null인 뉴스 (미분석)
     * 2순위: 분석 실패한 뉴스 (재분석)
     */
    @Scheduled(fixedDelayString = "${ai.analysis.interval:10000}")
    @Transactional
    public void analyzeUnprocessedNews() {
        // 1. 미분석 뉴스 우선 조회 (is_analyzed = 0, Generated Column + Index 활용)
        List<News> targetNews = newsRepository.findByIsAnalyzedFalse(
                PageRequest.of(0, BATCH_SIZE)).getContent();

        // 2. 미분석 뉴스 없으면 → 분석 실패 뉴스 조회 (재분석)
        if (targetNews.isEmpty()) {
            targetNews = newsRepository.findByAiResultFailed(
                    PageRequest.of(0, BATCH_SIZE)).getContent();
        }

        if (targetNews.isEmpty()) {
            log.debug(">>>> [Scheduler] 분석 대기 중인 뉴스 없음");
            return;
        }

        log.info(">>>> [Scheduler] 배치 분석 시작: {}개 뉴스", targetNews.size());

        // 3. 배치 분석 요청
        List<AiResponse> results = aiAnalyzer.analyzeBatchNews(targetNews);

        // 4. 결과 매핑 및 업데이트
        for (int i = 0; i < targetNews.size() && i < results.size(); i++) {
            News news = targetNews.get(i);
            AiResponse aiResult = results.get(i);

            news.updateAiResult(aiResult);
            saveKeywordsAsTags(news, aiResult);
            log.info(">>>> [분석 완료] ID: {}, 제목: {}, 점수: {}",
                    news.getId(),
                    news.getTitle().substring(0, Math.min(20, news.getTitle().length())) + "...",
                    aiResult.getScore());
        }

        // 5. 변경사항 저장
        newsRepository.saveAll(targetNews);

        log.info(">>>> [Scheduler] 배치 분석 완료: {}개 처리됨", targetNews.size());
    }

    /**
     * AI 분석 키워드를 Tag/NewsTag 테이블에 저장
     *
     * [처리 흐름]
     * 1. AiResponse의 keywords 리스트를 순회
     * 2. 소문자 정규화 ("Spring" → "spring") → 중복 방지
     * 3. Tag find-or-create 패턴으로 태그 조회/생성
     * 4. NewsTag 연관관계 생성
     */
    private void saveKeywordsAsTags(News news, AiResponse aiResult) {
        if (aiResult.getKeywords() == null || aiResult.getKeywords().isEmpty()) {
            return;
        }

        for (String keyword : aiResult.getKeywords()) {
            String normalized = keyword.strip().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }

            Tag tag = tagRepository.findByName(normalized)
                    .orElseGet(() -> tagRepository.save(new Tag(normalized)));

            newsTagRepository.save(new NewsTag(news, tag));
        }

        log.debug(">>>> [Tag 저장] 뉴스 ID: {}, 키워드 {}개 저장",
                news.getId(), aiResult.getKeywords().size());
    }
}
