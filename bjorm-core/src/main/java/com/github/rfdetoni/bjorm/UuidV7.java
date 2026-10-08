package com.github.rfdetoni.bjorm;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** RFC 9562 UUID version 7: 48-bit Unix millisecond timestamp, version, 12-bit
 * monotonic counter, RFC variant and 62 bits of cryptographic randomness. */
public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong LAST = new AtomicLong();
    private UuidV7() {}

    public static UUID next() {
        long stamp;
        for (;;) {
            long prior = LAST.get();
            long now = System.currentTimeMillis();
            long previousMillis = prior >>> 12;
            long millis = Math.max(now, previousMillis);
            long counter = millis > previousMillis ? RANDOM.nextInt(4096) : (prior & 0xfffL) + 1;
            if (counter == 4096) { millis++; counter = RANDOM.nextInt(4096); }
            stamp = (millis << 12) | counter;
            if (LAST.compareAndSet(prior, stamp)) break;
        }
        long msb = ((stamp >>> 12) << 16) | 0x7000L | (stamp & 0xfffL);
        long lsb = 0x8000000000000000L | (RANDOM.nextLong() & 0x3fffffffffffffffL);
        return new UUID(msb, lsb);
    }
}
