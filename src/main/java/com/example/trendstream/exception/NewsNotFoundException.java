package com.example.trendstream.exception;

/**
 * 뉴스를 찾을 수 없을 때 발생하는 예외
 *
 * [커스텀 예외 사용 이유]
 * 1. 의미 전달: IllegalArgumentException보다 명확한 의도 전달
 * 2. 예외 처리 분리: 특정 예외만 별도 처리 가능
 * 3. HTTP 상태 코드 매핑: 404 Not Found와 자연스럽게 연결
 *
 * [RuntimeException 상속 이유]
 * - Unchecked Exception → try-catch 강제하지 않음
 * - Spring @Transactional과 호환 (기본적으로 RuntimeException만 롤백)
 */
public class NewsNotFoundException extends RuntimeException {

    public NewsNotFoundException(Long id) {
        super("뉴스를 찾을 수 없습니다. id=" + id);
    }

    public NewsNotFoundException(String message) {
        super(message);
    }
}
