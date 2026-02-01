package com.example.trendstream.repository;

import com.example.trendstream.domain.entity.NewsTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsTagRepository extends JpaRepository<NewsTag, Long> {
}