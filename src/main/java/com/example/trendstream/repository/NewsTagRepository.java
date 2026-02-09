package com.example.trendstream.repository;

import com.example.trendstream.domain.entity.NewsTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsTagRepository extends JpaRepository<NewsTag, Long> {

    @Query(value = "SELECT t.name AS tag_name, COUNT(nt.id) AS cnt " +
            "FROM news_tags nt " +
            "JOIN tags t ON nt.tag_id = t.id " +
            "JOIN news n ON nt.news_id = n.id " +
            "WHERE n.pub_date >= :since " +
            "GROUP BY t.name " +
            "ORDER BY cnt DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findTopTrendingSince(@Param("since") LocalDateTime since,
                                        @Param("limit") int limit);

    @Query(value = "SELECT n.id, n.title, n.link " +
            "FROM news_tags nt " +
            "JOIN tags t ON nt.tag_id = t.id " +
            "JOIN news n ON nt.news_id = n.id " +
            "WHERE t.name = :tagName AND n.pub_date >= :since " +
            "ORDER BY n.pub_date DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findRecentNewsByTagName(@Param("tagName") String tagName,
                                           @Param("since") LocalDateTime since,
                                           @Param("limit") int limit);
}
