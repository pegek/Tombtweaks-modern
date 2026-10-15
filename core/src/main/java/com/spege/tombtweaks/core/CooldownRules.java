package com.spege.tombtweaks.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How long each book's cooldown lasts.
 *
 * <p>Entry format: {@code registry_name;minutes}. A malformed entry is skipped, never fatal -
 * a config mistake must not be able to crash the server. Minutes are clamped to 12 hours,
 * because a larger value is almost certainly a mistake rather than an intention.
 */
public final class CooldownRules {

    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MAX_MINUTES = 720L;

    private final Map<String, Long> millisById;

    private CooldownRules(Map<String, Long> millisById) {
        this.millisById = millisById;
    }

    public static CooldownRules parse(List<?> entries) {
        Map<String, Long> parsed = new HashMap<String, Long>();
        if (entries == null) {
            return new CooldownRules(parsed);
        }
        for (Object element : entries) {
            if (!(element instanceof String)) {
                continue; // null and non-String values from the config: skipped, never fatal
            }
            String raw = (String) element;
            int separator = raw.indexOf(';');
            if (separator <= 0 || separator == raw.length() - 1) {
                continue;
            }
            String id = raw.substring(0, separator).trim();
            if (id.isEmpty()) {
                continue;
            }
            long minutes;
            try {
                minutes = Long.parseLong(raw.substring(separator + 1).trim());
            } catch (NumberFormatException malformed) {
                continue;
            }
            if (minutes <= 0L) {
                continue;
            }
            if (minutes > MAX_MINUTES) {
                minutes = MAX_MINUTES;
            }
            parsed.put(id, Long.valueOf(minutes * MILLIS_PER_MINUTE));
        }
        return new CooldownRules(parsed);
    }

    /** @return cooldown length in ms, or 0 when that book has none */
    public long millisFor(String itemId) {
        Long millis = millisById.get(itemId);
        return millis == null ? 0L : millis.longValue();
    }

    public boolean isEmpty() {
        return millisById.isEmpty();
    }
}
