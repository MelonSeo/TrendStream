package com.example.trendstream.service;

import com.example.trendstream.domain.entity.News;
import com.example.trendstream.dto.NewsResponseDto;
import com.example.trendstream.exception.NewsNotFoundException;
import com.example.trendstream.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor  // final 필드를 파라미터로 받는 생성자 자동 생성 (의존성 주입)
@Transactional(readOnly = true)  // 클래스 레벨 기본 트랜잭션 설정
public class NewsService {

    private final NewsRepository newsRepository;  // 생성자 주입 (스프링 권장 방식)

    public NewsResponseDto getNewsById(Long id) {
        News news = newsRepository.findByIdWithTags(id)
                .orElseThrow(() -> new NewsNotFoundException(id));
        return NewsResponseDto.from(news);
    }

    public Page<NewsResponseDto> getLatestNews(Pageable pageable) {
        return newsRepository.findAllByOrderByPubDateDesc(pageable)
                .map(NewsResponseDto::from);  // Page<News> -> Page<NewsResponseDto> 변환
    }

    public Page<NewsResponseDto> searchNews(String keyword, Pageable pageable) {
        // Native Query에서 ORDER BY 직접 지정하므로 sort 제외
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return newsRepository.searchByKeyword(keyword, unsortedPageable)
                .map(NewsResponseDto::from);
    }

    public Page<NewsResponseDto> searchByTag(String tagName, Pageable pageable) {
        // Native Query에서 ORDER BY 직접 지정하므로 sort 제외
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return newsRepository.findByTagName(tagName.toLowerCase(), unsortedPageable)
                .map(NewsResponseDto::from);
    }

    public Page<NewsResponseDto> getPopularNews(Pageable pageable) {
        return newsRepository.findAllByOrderByScoreDesc(pageable)
                .map(NewsResponseDto::from);
    }

    public Page<NewsResponseDto> getNewsByCategory(String category, Pageable pageable) {
        // Native Query에서 ORDER BY 직접 지정하므로 sort 제외
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return newsRepository.findBySearchKeyword(category, unsortedPageable)
                .map(NewsResponseDto::from);
    }

    @Cacheable(value = "categories")
    public java.util.List<String> getCategories() {
        return newsRepository.findDistinctSearchKeywords();
    }

    public Page<NewsResponseDto> getNewsBySource(String source, Pageable pageable) {
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return newsRepository.findBySource(source, unsortedPageable)
                .map(NewsResponseDto::from);
    }

    @Cacheable(value = "sources")
    public java.util.List<String> getSources() {
        return newsRepository.findDistinctSources();
    }
}
