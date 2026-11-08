package com.github.j5ik2o.cqrs.es.java.rmu;

import java.time.Duration;
import java.util.function.UnaryOperator;

/**
 * The stream reader's idle and failure delay. The delay grows exponentially to a cap and applies
 * full jitter, so a restarted reader does not hammer the stream or the database in lock step with
 * another replica.
 */
public final class Backoff {
  private final Duration initial;
  private final Duration max;
  private final UnaryOperator<Duration> jitter;
  private int attempt;

  public Backoff(Duration initial, Duration max) {
    this(initial, max, RetryPolicy::fullJitter);
  }

  public Backoff(Duration initial, Duration max, UnaryOperator<Duration> jitter) {
    this.initial =
        initial == null || initial.isZero() || initial.isNegative()
            ? Duration.ofMillis(500)
            : initial;
    this.max =
        max == null || max.isZero() || max.compareTo(this.initial) < 0
            ? Duration.ofSeconds(30)
            : max;
    this.jitter = jitter == null ? RetryPolicy::fullJitter : jitter;
  }

  public synchronized Duration next() {
    var delay = initial;
    for (var i = 0; i < attempt && delay.compareTo(max) < 0; i++) {
      delay = delay.multipliedBy(2);
    }
    if (delay.compareTo(max) > 0) {
      delay = max;
    }
    attempt++;
    return jitter.apply(delay);
  }

  public synchronized void reset() {
    attempt = 0;
  }
}
