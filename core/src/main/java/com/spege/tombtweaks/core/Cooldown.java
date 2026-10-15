package com.spege.tombtweaks.core;

/** Remaining-time arithmetic. Wall clock, in milliseconds. */
public final class Cooldown {

    private Cooldown() {
    }

    /**
     * @return how many ms are left, 0 when free
     */
    public static long remaining(long lastUseMillis, long cooldownMillis, long nowMillis) {
        if (cooldownMillis <= 0L || lastUseMillis <= 0L) {
            return 0L;
        }
        if (nowMillis < lastUseMillis) {
            return 0L; // the clock went backwards - do not lock the player out forever
        }
        long elapsed = nowMillis - lastUseMillis;
        return elapsed >= cooldownMillis ? 0L : cooldownMillis - elapsed;
    }

    public static boolean isActive(long lastUseMillis, long cooldownMillis, long nowMillis) {
        return remaining(lastUseMillis, cooldownMillis, nowMillis) > 0L;
    }
}
