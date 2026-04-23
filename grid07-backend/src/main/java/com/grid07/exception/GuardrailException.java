package com.grid07.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class GuardrailException extends RuntimeException {

    private final HttpStatus status;

    public GuardrailException(String message) {
        super(message);
        this.status = HttpStatus.TOO_MANY_REQUESTS;
    }

    public GuardrailException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
