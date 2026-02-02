package com.example.trendstream.service;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.dto.NewsResponseDto;
import com.example.trendstream.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 뉴스 조회 서비스
 *
 * [Service 계층의 역할]
 * 1. 비즈니스 로직 처리 (Controller는 요청/응답만 담당)
 * 2. 트랜잭션 경계 설정
 * 3. 여러 Repository를 조합한 복합 로직 처리
 *
 * [@Transactional(readOnly = true) 사용 이유]
 * - 읽기 전용 트랜잭션으로 설정하면 JPA가 변경 감지(Dirty Checking)를 수행하지 않음
 * - 플러시(flush)를 생략하여 성능 최적화
 * - 데이터베이스에 따라 읽기 전용 연결 사용 (Replica DB 활용 가능)
 */
@Service
@RequiredArgsConstructor  // final 필드를 파라미터로 받는 생성자 자동 생성 (의존성 주입)
@Transactional(readOnly = true)  // 클래스 레벨 기본 트랜잭션 설정
public class NewsService {

    private final NewsRepository newsRepository;  // 생성자 주입 (스프링 권장 방식)

    /**
     * ID로 뉴스 상세 조회
     *
     * [Fetch Join 사용 이유]
     * - 뉴스와 연관된 태그를 한 번의 쿼리로 가져옴 (N+1 문제 방지)
     * - LAZY 로딩이더라도 JOIN FETCH로 즉시 로딩 가능
     *
     * @param id 조회할 뉴스 ID
     * @return 뉴스 상세 정보 DTO
     * @throws IllegalArgumentException 뉴스가 존재하지 않을 경우
     */
    public NewsResponseDto getNewsById(Long id) {
        News news = newsRepository.findByIdWithTags(id)
                .orElseThrow(() -> new IllegalArgumentException("뉴스를 찾을 수 없습니다. id=" + id));
        return NewsResponseDto.from(news);
    }

    /**
     * 최신순 뉴스 목록 조회 (페이지네이션)
     *
     * [Page 객체가 제공하는 정보]
     * - content: 실제 데이터 리스트
     * - totalElements: 전체 데이터 개수
     * - totalPages: 전체 페이지 수
     * - number: 현재 페이지 번호 (0부터 시작)
     * - size: 페이지당 데이터 개수
     * - first/last: 첫 페이지/마지막 페이지 여부
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 페이지네이션된 뉴스 목록
     */
    public Page<NewsResponseDto> getLatestNews(Pageable pageable) {
        return newsRepository.findAllByOrderByPubDateDesc(pageable)
                .map(NewsResponseDto::from);  // Page<News> -> Page<NewsResponseDto> 변환
    }

    /**
     * 키워드로 뉴스 검색
     *
     * [검색 범위]
     * - 뉴스 제목 (title)
     * - 뉴스 설명/본문 (description)
     * - LIKE %keyword% 패턴으로 부분 일치 검색
     *
     * [향후 개선 포인트]
     * - 전문 검색 엔진 도입 (Elasticsearch)
     * - 형태소 분석을 통한 한국어 검색 최적화
     * - AI 요약(summary) 필드 검색 추가
     *
     * @param keyword 검색 키워드
     * @param pageable 페이지 정보
     * @return 검색 결과 (페이지네이션)
     */
    public Page<NewsResponseDto> searchNews(String keyword, Pageable pageable) {
        return newsRepository.searchByKeyword(keyword, pageable)
                .map(NewsResponseDto::from);
    }

    /**
     * 인기 뉴스 조회 (AI 중요도 점수순)
     *
     * [중요도 점수 (score)]
     * - GeminiService가 뉴스 분석 후 0~100 사이 점수 부여
     * - 점수 기준: 영향력, 시의성, 기술적 중요도 등
     * - JSON 필드 내부 값으로 정렬 (MySQL JSON_EXTRACT 함수 사용)
     *
     * [Native Query 사용 이유]
     * - JPQL은 JSON 필드 내부 접근을 직접 지원하지 않음
     * - MySQL의 JSON_EXTRACT() 함수로 aiResult.score 값 추출
     *
     * @param pageable 페이지 정보
     * @return 중요도순 뉴스 목록
     */
    public Page<NewsResponseDto> getPopularNews(Pageable pageable) {
        return newsRepository.findAllByOrderByScoreDesc(pageable)
                .map(NewsResponseDto::from);
    }
}
