package com.example.trendstream.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

public class GeminiInterfaceDto {

    // [요청] 우리가 보낼 데이터 (프롬프트)
    @Data
    @AllArgsConstructor
    public static class Request {
        private List<Content> contents;

        // 편의 메서드: 질문(text)만 넣으면 객체 생성
        public static Request of(String text) {
            Part part = new Part(text);
            Content content = new Content(Collections.singletonList(part));
            return new Request(Collections.singletonList(content));
        }
    }

    @Data
    @AllArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @Data
    @AllArgsConstructor
    public static class Part {
        private String text;
    }

    // [응답] 구글이 보내줄 데이터 (AI 답변)
    @Data
    @NoArgsConstructor
    public static class Response {
        private List<Candidate> candidates;

        @Data
        public static class Candidate {
            private Content content;
        }

        // 편의 메서드: 응답에서 텍스트만 쏙 빼내기
        public String getText() {
            if (candidates == null || candidates.isEmpty()) return "";
            if (candidates.get(0).getContent() == null) return "";
            return candidates.get(0).getContent().getParts().get(0).getText();
        }
    }
}