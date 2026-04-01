package com.pvpclient.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Millisecond-precision timing utilities for combat actions.
 * All timing is nanoTime-based, never tick-based.
 */
public class TimingUtil {
    private long lastActionNanos = 0;

    public void markNow() {
        lastActionNanos = System.nanoTime();
    }

    public long elapsedMs() {
        return (System.nanoTime() - lastActionNanos) / 1_000_000L;
    }

    public boolean hasElapsed(long delayMs) {
        return elapsedMs() >= delayMs;
    }

    public boolean hasElapsedWithJitter(long delayMs, int jitterMs) {
        long jitter = jitterMs > 0
                ? ThreadLocalRandom.current().nextInt(-jitterMs, jitterMs + 1)
                : 0;
        return elapsedMs() >= (delayMs + jitter);
    }

    public void reset() {
        lastActionNanos = 0;
    }

    /** Returns current time in ms (monotonic) */
    public static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }
}
