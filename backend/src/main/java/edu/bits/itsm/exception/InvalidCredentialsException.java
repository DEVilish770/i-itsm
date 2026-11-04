package edu.bits.itsm.exception;

import org.springframework.http.HttpStatus;

/** Login failed. */
public class InvalidCredentialsException extends ItsmException {
    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid username or password", HttpStatus.UNAUTHORIZED);
    }
}
