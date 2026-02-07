package com.example.trendstream.service.scheduler;

import com.example.trendstream.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 데이터 정리 스케줄러
 *
 * [동작 방식]
 * - 매일 새벽 3시에 실행
 * - 보관 기간이 지난 뉴스 데이터 삭제
 * - cascade 설정으로 연관된 NewsTag도 함께 삭제
 *
 * [설정]
 * - data.retention.days: 보관 기간 (기본 60일)
 */
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

    /**
     * 수동 정리 메서드 (테스트/관리자용)
     *
     * @param days 며칠 이전 데이터를 삭제할지
     * @return 삭제된 건수
     */
    @Transactional
    public long manualCleanup(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        long deletedCount = newsRepository.deleteByPubDateBefore(cutoffDate);
        log.info(">>>> [Manual Cleanup] {}일 이전 뉴스 {}건 삭제됨", days, deletedCount);
        return deletedCount;
    }
}
