package edu.bits.itsm.exception;

import org.springframework.http.HttpStatus;

/** A request to an AI helper is malformed (bad screenshot, wrong chat shape, empty query). Rejected in every mode. */
public class InvalidAiRequestException extends ItsmException {
    public InvalidAiRequestException(String message) {
        super("INVALID_AI_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
