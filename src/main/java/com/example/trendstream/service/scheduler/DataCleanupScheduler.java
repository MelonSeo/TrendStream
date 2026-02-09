package com.example.trendstream.service.scheduler;

import com.example.trendstream.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class DataCleanupScheduler {

    private final NewsRepository newsRepository;

    /** 보관 기간 (일 단위, 기본 60일) */
    @Value("${data.retention.days:60}")
    private int retentionDays;

    /**
     * 오래된 뉴스 데이터 정리 (매일 새벽 3시 실행)
     *
     * [Cron 표현식]
     * - 초 분 시 일 월 요일
     * - "0 0 3 * * *" = 매일 03:00:00
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldNews() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);

        // 삭제 전 개수 확인
        long targetCount = newsRepository.countByPubDateBefore(cutoffDate);

        if (targetCount == 0) {
            log.info(">>>> [Cleanup] 삭제 대상 없음 (보관 기간: {}일)", retentionDays);
            return;
        }

        log.info(">>>> [Cleanup] 데이터 정리 시작: {}일 이전 뉴스 {}건 삭제 예정",
                retentionDays, targetCount);

        // 삭제 실행
        long deletedCount = newsRepository.deleteByPubDateBefore(cutoffDate);

        log.info(">>>> [Cleanup] 데이터 정리 완료: {}건 삭제됨 (기준일: {})",
                deletedCount, cutoffDate.toLocalDate());
    }

    @Transactional
    public long manualCleanup(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        long deletedCount = newsRepository.deleteByPubDateBefore(cutoffDate);
        log.info(">>>> [Manual Cleanup] {}일 이전 뉴스 {}건 삭제됨", days, deletedCount);
        return deletedCount;
    }
}
