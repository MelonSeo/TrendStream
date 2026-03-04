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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsAnalysisScheduler {

    private final NewsRepository newsRepository;
    private final AiAnalyzer aiAnalyzer;
    private final TagRepository tagRepository;
    private final NewsTagRepository newsTagRepository;

    /** 배치 크기: 한 번에 분석할 뉴스 개수 */
    private static final int BATCH_SIZE = 5;

    /** AI가 이 키워드를 태그로 뽑으면 광고성 콘텐츠로 판단하여 삭제 */
    private static final Set<String> AD_TAG_KEYWORDS = Set.of(
            "계정", "구매", "계정구매", "팔로워", "구독자 구매", "좋아요 구매", "비즈니스"
    );

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
        List<News> toDelete = new ArrayList<>();
        for (int i = 0; i < targetNews.size() && i < results.size(); i++) {
            News news = targetNews.get(i);
            AiResponse aiResult = results.get(i);

            // 광고성 태그 감지 시 삭제 대상으로 분류
            if (hasAdTags(aiResult)) {
                log.info(">>>> [광고 감지] 삭제 예정 - ID: {}, 제목: {}, 태그: {}",
                        news.getId(),
                        news.getTitle().substring(0, Math.min(20, news.getTitle().length())) + "...",
                        aiResult.getKeywords());
                toDelete.add(news);
                continue;
            }

            news.updateAiResult(aiResult);
            saveKeywordsAsTags(news, aiResult);
            log.info(">>>> [분석 완료] ID: {}, 제목: {}, 점수: {}",
                    news.getId(),
                    news.getTitle().substring(0, Math.min(20, news.getTitle().length())) + "...",
                    aiResult.getScore());
        }

        // 5. 광고성 콘텐츠 삭제
        if (!toDelete.isEmpty()) {
            newsRepository.deleteAll(toDelete);
            log.info(">>>> [광고 삭제] {}건 삭제 완료", toDelete.size());
        }

        // 6. 변경사항 저장
        targetNews.removeAll(toDelete);
        newsRepository.saveAll(targetNews);

        log.info(">>>> [Scheduler] 배치 분석 완료: {}개 처리됨", targetNews.size());
    }

    private boolean hasAdTags(AiResponse aiResult) {
        if (aiResult.getKeywords() == null) return false;
        return aiResult.getKeywords().stream()
                .map(k -> k.strip().toLowerCase(Locale.ROOT))
                .anyMatch(AD_TAG_KEYWORDS::contains);
    }

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
