package com.spege.tombtweaks.core;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Which occupied slot a decaying grave gives up next.
 *
 * <p>Two candidate lists, not one. Unprotected stacks are eaten first; protected ones
 * are unreachable altogether as long as anything else is left. When only they remain,
 * {@code neverDecay} decides: true = protection is an exemption (the grave stops in place),
 * false = protection was only an ordering.
 */
public final class DecayVictim {

    public static final int NOTHING = -1;

    private DecayVictim() {
    }

    /**
     * @param unprotected indices of slots holding an unprotected stack
     * @param guarded     indices of slots holding a protected stack
     * @param neverDecay  whether protection is an exemption or only an ordering
     * @param random      bound -&gt; value in the range [0, bound); an out-of-range value
     *                    is wrapped, not thrown - this runs in the grave's server tick,
     *                    and an exception there would crash the server
     */
    public static int pick(List<Integer> unprotected, List<Integer> guarded,
                           boolean neverDecay, IntUnaryOperator random) {
        List<Integer> pool = unprotected;
        if (pool.isEmpty()) {
            if (guarded.isEmpty() || neverDecay) {
                return NOTHING;
            }
            pool = guarded;
        }
        return pool.get(Math.floorMod(random.applyAsInt(pool.size()), pool.size())).intValue();
    }
}
