package com.example.trendstream.exception;


public class NewsNotFoundException extends RuntimeException {

    public NewsNotFoundException(Long id) {
        super("뉴스를 찾을 수 없습니다. id=" + id);
    }

    public NewsNotFoundException(String message) {
        super(message);
    }
}
