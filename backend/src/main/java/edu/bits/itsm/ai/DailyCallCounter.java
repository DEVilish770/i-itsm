package edu.bits.itsm.ai;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Counts model calls per calendar day in one time zone; the count starts again at that zone's midnight. Not thread-safe. */
final class DailyCallCounter {

    private final Clock clock;
    private final ZoneId zone;
    private LocalDate day;
    private int count;

    DailyCallCounter(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    int today() { roll(); return count; }

    void increment() { roll(); count++; }

    private void roll() {
        LocalDate now = LocalDate.ofInstant(clock.instant(), zone);
        if (!now.equals(day)) { day = now; count = 0; }
    }
}
