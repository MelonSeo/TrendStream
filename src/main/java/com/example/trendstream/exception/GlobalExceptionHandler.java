package com.example.trendstream.exception;

import com.example.trendstream.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 전역 예외 처리 핸들러
 *
 * [@RestControllerAdvice 동작 원리]
 * 1. 모든 @RestController에서 발생한 예외를 가로챔
 * 2. @ExceptionHandler에 매핑된 메서드가 예외를 처리
 * 3. 일관된 에러 응답 포맷으로 변환하여 반환
 *
 * [예외 처리 우선순위]
 * - 구체적인 예외 클래스가 우선 처리됨
 * - NewsNotFoundException → IllegalArgumentException → Exception 순
 *
 * [면접 포인트]
 * - 중앙 집중식 예외 처리로 코드 중복 제거
 * - HTTP 상태 코드와 비즈니스 에러 코드 분리
 * - 클라이언트를 위한 일관된 에러 응답 포맷
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 뉴스를 찾을 수 없는 경우 (404 Not Found)
     *
     * [사용 시나리오]
     * - GET /api/news/{id}에서 존재하지 않는 ID 조회
     * - 삭제된 뉴스 접근 시도
     */
    @ExceptionHandler(NewsNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNewsNotFound(NewsNotFoundException ex) {
        log.warn("뉴스 조회 실패: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        HttpStatus.NOT_FOUND.getReasonPhrase(),
                        "NEWS_NOT_FOUND",
                        ex.getMessage()
                ));
    }

    /**
     * 잘못된 인자 전달 (400 Bad Request)
     *
     * [사용 시나리오]
     * - 유효하지 않은 파라미터 값
     * - 비즈니스 규칙 위반
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("잘못된 요청: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "INVALID_ARGUMENT",
                        ex.getMessage()
                ));
    }

    /**
     * 필수 파라미터 누락 (400 Bad Request)
     *
     * [사용 시나리오]
     * - GET /api/news/search (keyword 파라미터 누락)
     * - GET /api/news/tag (name 파라미터 누락)
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("필수 파라미터 누락: {}", ex.getParameterName());

        String message = String.format("필수 파라미터 '%s'이(가) 누락되었습니다.", ex.getParameterName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "MISSING_PARAMETER",
                        message
                ));
    }

    /**
     * 파라미터 타입 불일치 (400 Bad Request)
     *
     * [사용 시나리오]
     * - GET /api/news/abc (id에 숫자 대신 문자열)
     * - 잘못된 타입의 쿼리 파라미터
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("파라미터 타입 불일치: {} = {}", ex.getName(), ex.getValue());

        String message = String.format("'%s' 파라미터의 값 '%s'이(가) 올바르지 않습니다.",
                ex.getName(), ex.getValue());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "TYPE_MISMATCH",
                        message
                ));
    }

    /**
     * 기타 모든 예외 (500 Internal Server Error)
     *
     * [중요]
     * - 예상치 못한 예외를 잡아서 서버 내부 정보 노출 방지
     * - 상세 에러는 로그에만 기록, 클라이언트에는 일반 메시지 반환
     * - 프로덕션에서는 ex.getMessage()를 그대로 노출하지 않는 것이 좋음
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("예상치 못한 에러 발생", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                        "INTERNAL_ERROR",
                        "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                ));
    }
}
