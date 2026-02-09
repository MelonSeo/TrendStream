package com.example.trendstream.config;

import com.example.trendstream.service.ai.AiAnalyzer;
import com.example.trendstream.service.ai.GeminiService;
import com.example.trendstream.service.ai.GroqService;
import com.example.trendstream.service.ai.OllamaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AiConfig {

    @Value("${ai.provider:groq}")
    private String aiProvider;

    @Bean
    public AiAnalyzer aiAnalyzer(GeminiService geminiService, ObjectMapper objectMapper) {
        return switch (aiProvider.toLowerCase()) {
            case "groq" -> {
                log.info(">>>> [AiConfig] AI Provider: Groq (클라우드 LLM)");
                yield new GroqService(objectMapper);
            }
            case "ollama" -> {
                log.info(">>>> [AiConfig] AI Provider: Ollama (로컬 LLM)");
                yield new OllamaService(objectMapper);
            }
            default -> {
                log.info(">>>> [AiConfig] AI Provider: Gemini (외부 API)");
                yield geminiService;
            }
        };
    }
}
