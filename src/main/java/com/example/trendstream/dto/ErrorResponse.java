package com.example.trendstream.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;


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
