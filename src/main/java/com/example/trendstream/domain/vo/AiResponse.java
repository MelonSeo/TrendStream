package com.example.trendstream.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiResponse {
    private String summary;          // 3줄 요약
    private String sentiment;        // 긍정/부정
    private List<String> keywords;   // ["Spring", "Kafka"]
    private Integer score;           // 중요도 점수
}
