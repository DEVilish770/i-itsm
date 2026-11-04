package edu.bits.itsm.service;

import org.springframework.stereotype.Component;

/** Formats the DB identity as a human-readable incident number. */
@Component
public class IncidentNumberGenerator {
    public String format(Long id) { return String.format("INC-%06d", id); }
}
