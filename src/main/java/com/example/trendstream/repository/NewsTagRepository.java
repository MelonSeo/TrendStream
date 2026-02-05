package com.example.trendstream.repository;

import com.example.trendstream.domain.entity.NewsTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsTagRepository extends JpaRepository<NewsTag, Long> {

    /**
     * 트렌드 키워드 집계 (기간 내 빈도순)
     *
     * [Native Query 사용 이유]
     * - GROUP BY + COUNT 집계 후 ORDER BY cnt DESC 정렬 필요
     * - LIMIT을 직접 지정해야 하므로 Native Query 사용
     *
     * @param since 집계 시작 시점
     * @param limit 상위 N개
     * @return [tag_name, count] 배열의 리스트
     */
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

    /**
     * 특정 키워드의 관련 뉴스 조회 (최신순)
     *
     * @param tagName 키워드(태그) 이름
     * @param since 조회 시작 시점
     * @param limit 최대 개수
     * @return [news_id, title, link] 배열의 리스트
     */
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
