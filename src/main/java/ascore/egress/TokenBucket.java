package ascore.egress;

import java.util.function.LongSupplier;

/**
 * Refill-on-read rate limit. Nothing ticks in the background: each tryAcquire first
 * adds whatever tokens the elapsed time has earned, capped at capacity, then takes one
 * if there is one.
 *
 * The clock is passed in so a test can move time by hand instead of sleeping.
 */
public class TokenBucket {

	private final int capacity;
	private final double refillPerNano;
	private final LongSupplier clock;
	private double tokens;
	private long last;

	public TokenBucket(int capacity, double refillPerSecond, LongSupplier clock) {
		this.capacity = capacity;
		this.refillPerNano = refillPerSecond / 1_000_000_000d;
		this.clock = clock;
		this.tokens = capacity;
		this.last = clock.getAsLong();
	}

	public synchronized boolean tryAcquire() {
		long now = clock.getAsLong();
		tokens = Math.min(capacity, tokens + (now - last) * refillPerNano);
		last = now;
		if (tokens < 1) return false;
		tokens -= 1;
		return true;
	}

}
