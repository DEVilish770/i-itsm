package edu.bits.itsm.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of the application's custom exception hierarchy.
 * Every business-rule violation extends this class so that the
 * {@link edu.bits.itsm.web.GlobalExceptionHandler} can map it to a
 * consistent JSON error response with the right HTTP status.
 */
public abstract class ItsmException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ItsmException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
