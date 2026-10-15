package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DecayScheduleTest {

    private static final long START = 6_000L;
    private static final long INTERVAL = 1_200L;

    @Test
    void nothingBeforeThreshold() {
        assertFalse(DecaySchedule.shouldDecay(0L, START, INTERVAL));
        assertFalse(DecaySchedule.shouldDecay(START - 1L, START, INTERVAL));
    }

    @Test
    void firstFiringExactlyAtThreshold() {
        assertTrue(DecaySchedule.shouldDecay(START, START, INTERVAL));
    }

    @Test
    void exactlyOneFiringPerInterval() {
        int fired = 0;
        for (long tick = START; tick < START + 10L * INTERVAL; tick++) {
            if (DecaySchedule.shouldDecay(tick, START, INTERVAL)) {
                fired++;
            }
        }
        assertEquals(10, fired);
    }

    @Test
    void zeroIntervalDoesNotDivideByZero() {
        assertFalse(DecaySchedule.shouldDecay(999_999L, START, 0L));
        assertFalse(DecaySchedule.shouldDecay(999_999L, START, -5L));
    }

    @Test
    void rewoundCounterStaysSilent() {
        // countTicks goes back to 0 when a later death merges into the grave (resetDeathTime) -
        // that also restarts decay.
        assertFalse(DecaySchedule.shouldDecay(10L, START, INTERVAL));
    }

    @Test
    void negativeStartCountsFromZero() {
        assertTrue(DecaySchedule.shouldDecay(0L, -1L, 1_200L));
        assertFalse(DecaySchedule.shouldDecay(1_199L, -1L, 1_200L));
    }
}
