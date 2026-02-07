package com.example.trendstream.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * API 에러 응답 DTO
 *
 * [일관된 에러 응답의 중요성]
 * - 클라이언트가 에러 처리 로직을 표준화할 수 있음
 * - 디버깅 시 필요한 정보를 체계적으로 제공
 * - REST API 설계 모범 사례
 *
 * [응답 예시]
 * {
 *   "status": 404,
 *   "error": "Not Found",
 *   "code": "NEWS_NOT_FOUND",
 *   "message": "뉴스를 찾을 수 없습니다. id=999",
 *   "timestamp": "2026-02-08T15:30:00"
 * }
 */
@Getter
@Builder
public class ErrorResponse {

    /** HTTP 상태 코드 (예: 404, 500) */
    private final int status;

    /** HTTP 상태 메시지 (예: "Not Found", "Internal Server Error") */
    private final String error;

    /** 애플리케이션 정의 에러 코드 (예: "NEWS_NOT_FOUND") */
    private final String code;

    /** 사용자/개발자를 위한 상세 메시지 */
    private final String message;

    /** 에러 발생 시각 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

    /**
     * 간편 생성 메서드
     *
     * @param status HTTP 상태 코드
     * @param error HTTP 상태 메시지
     * @param code 애플리케이션 에러 코드
     * @param message 상세 메시지
     * @return ErrorResponse 인스턴스
     */
    public static ErrorResponse of(int status, String error, String code, String message) {
        return ErrorResponse.builder()
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
