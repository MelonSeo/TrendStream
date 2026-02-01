package com.example.trendstream.repository;

import com.example.trendstream.domain.entity.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

// JpaRepository<Entity, ID타입>을 상속받으면 기본적인 CRUD(저장, 조회)가 공짜!
public interface NewsRepository extends JpaRepository<News, Long> {

    // 1. 중복 뉴스 방지용 (이미 있는 링크니?)
    // 활용: 수집기(Producer)가 뉴스 긁어오기 전에 이 메서드로 검사해서 중복이면 버립니다.
    boolean existsByLink(String link);

    // 2. URL로 뉴스 찾기 (있으면 업데이트 하려고)
    News findByLink(String link);

    // 3. [🔥면접 필살기] N+1 문제 해결을 위한 Fetch Join
    // 설명: 그냥 findAll() 하면, 뉴스 10개를 가져올 때 태그를 조회하려고 쿼리가 10번 더 나갑니다. (1+10 = 11번)
    // "JOIN FETCH"를 쓰면, SQL 한 방에 "뉴스 + 태그"를 싹 긁어옵니다. (1번)
    @Query("SELECT DISTINCT n FROM News n LEFT JOIN FETCH n.newsTags nt LEFT JOIN FETCH nt.tag ORDER BY n.pubDate DESC")
    List<News> findAllWithTags();
}