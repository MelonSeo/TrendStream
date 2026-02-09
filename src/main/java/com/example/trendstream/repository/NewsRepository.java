package com.example.trendstream.repository;

import com.example.trendstream.domain.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface NewsRepository extends JpaRepository<News, Long> {

    boolean existsByLink(String link);

    News findByLink(String link);

    @Query("SELECT DISTINCT n FROM News n LEFT JOIN FETCH n.newsTags nt LEFT JOIN FETCH nt.tag ORDER BY n.pubDate DESC")
    List<News> findAllWithTags();

    @Query("SELECT n FROM News n LEFT JOIN FETCH n.newsTags nt LEFT JOIN FETCH nt.tag WHERE n.id = :id")
    Optional<News> findByIdWithTags(@Param("id") Long id);

    Page<News> findAllByOrderByPubDateDesc(Pageable pageable);

    @Query(value = "SELECT * FROM news WHERE title LIKE CONCAT('%', :keyword, '%') " +
            "OR description LIKE CONCAT('%', :keyword, '%') " +
            "OR (ai_result IS NOT NULL AND ai_result ->> '$.summary' LIKE CONCAT('%', :keyword, '%')) " +
            "ORDER BY pub_date DESC",
            countQuery = "SELECT COUNT(*) FROM news WHERE title LIKE CONCAT('%', :keyword, '%') " +
                    "OR description LIKE CONCAT('%', :keyword, '%') " +
                    "OR (ai_result IS NOT NULL AND ai_result ->> '$.summary' LIKE CONCAT('%', :keyword, '%'))",
            nativeQuery = true)
    Page<News> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    Page<News> findByIsAnalyzedFalse(Pageable pageable);

    @Query(value = "SELECT * FROM news WHERE ai_summary = '분석 실패'",
            countQuery = "SELECT COUNT(*) FROM news WHERE ai_summary = '분석 실패'",
            nativeQuery = true)
    Page<News> findByAiResultFailed(Pageable pageable);

    @Query(value = "SELECT * FROM news WHERE ai_score IS NOT NULL ORDER BY ai_score DESC",
            countQuery = "SELECT COUNT(*) FROM news WHERE ai_score IS NOT NULL",
            nativeQuery = true)
    Page<News> findAllByOrderByScoreDesc(Pageable pageable);

    @Query(value = "SELECT DISTINCT n.* FROM news n " +
            "JOIN news_tags nt ON n.id = nt.news_id " +
            "JOIN tags t ON nt.tag_id = t.id " +
            "WHERE t.name = :tagName " +
            "ORDER BY n.pub_date DESC",
            countQuery = "SELECT COUNT(DISTINCT n.id) FROM news n " +
                    "JOIN news_tags nt ON n.id = nt.news_id " +
                    "JOIN tags t ON nt.tag_id = t.id " +
                    "WHERE t.name = :tagName",
            nativeQuery = true)
    Page<News> findByTagName(@Param("tagName") String tagName, Pageable pageable);


    @Query(value = "SELECT * FROM news WHERE search_keyword = :searchKeyword ORDER BY pub_date DESC",
            countQuery = "SELECT COUNT(*) FROM news WHERE search_keyword = :searchKeyword",
            nativeQuery = true)
    Page<News> findBySearchKeyword(@Param("searchKeyword") String searchKeyword, Pageable pageable);

    @Query(value = "SELECT DISTINCT search_keyword FROM news WHERE search_keyword IS NOT NULL ORDER BY search_keyword",
            nativeQuery = true)
    List<String> findDistinctSearchKeywords();

    @Query(value = "SELECT * FROM news WHERE source = :source ORDER BY pub_date DESC",
            countQuery = "SELECT COUNT(*) FROM news WHERE source = :source",
            nativeQuery = true)
    Page<News> findBySource(@Param("source") String source, Pageable pageable);

    @Query(value = "SELECT DISTINCT source FROM news ORDER BY source",
            nativeQuery = true)
    List<String> findDistinctSources();

    long deleteByPubDateBefore(LocalDateTime before);

    long countByPubDateBefore(LocalDateTime before);
}
