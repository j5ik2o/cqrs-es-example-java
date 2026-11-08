package com.github.j5ik2o.cqrs.es.java.rmu;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

/**
 * Bounds how long one record may be retried before it is parked.
 *
 * <p>{@code maxElapsed} is the wall-clock budget, so a long queue of records cannot push a single
 * invocation past the caller's deadline. The delay grows exponentially and is capped, then reduced
 * by full jitter so replicas do not retry in lock step.
 */
public final class RetryPolicy {
  private static final Duration DEFAULT_MAX_ELAPSED = Duration.ofSeconds(30);
  private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofMillis(200);
  private static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(5);

  private final int maxAttempts;
  private final Duration maxElapsed;
  private final Duration initialDelay;
  private final Duration maxDelay;
  private final Sleeper sleeper;
  private final UnaryOperator<Duration> jitter;
  private final Clock clock;

  public RetryPolicy(
      int maxAttempts,
      Duration maxElapsed,
      Duration initialDelay,
      Duration maxDelay,
      Sleeper sleeper,
      UnaryOperator<Duration> jitter,
      Clock clock) {
    this.maxAttempts = Math.max(1, maxAttempts);
    this.maxElapsed = maxElapsed == null ? Duration.ZERO : maxElapsed;
    this.initialDelay = initialDelay == null ? Duration.ZERO : initialDelay;
    this.maxDelay = maxDelay == null ? Duration.ZERO : maxDelay;
    this.sleeper = sleeper == null ? RetryPolicy::systemSleep : sleeper;
    this.jitter = jitter == null ? RetryPolicy::fullJitter : jitter;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public static RetryPolicy defaults() {
    return new RetryPolicy(
        5,
        DEFAULT_MAX_ELAPSED,
        DEFAULT_INITIAL_DELAY,
        DEFAULT_MAX_DELAY,
        RetryPolicy::systemSleep,
        RetryPolicy::fullJitter,
        Clock.systemUTC());
  }

  /** Used when the outer delivery mechanism (Lambda or the stream reader) retries instead. */
  public static RetryPolicy noRetry() {
    return new RetryPolicy(
        1,
        Duration.ZERO,
        Duration.ZERO,
        Duration.ZERO,
        delay -> {},
        delay -> delay,
        Clock.systemUTC());
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public Duration maxElapsed() {
    return maxElapsed;
  }

  public Duration initialDelay() {
    return initialDelay;
  }

  public Duration maxDelay() {
    return maxDelay;
  }

  public void run(Runnable operation) {
    var startedAt = clock.instant();
    RuntimeException lastFailure = null;
    for (var attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        operation.run();
        return;
      } catch (RuntimeException failure) {
        lastFailure = failure;
        if (!FailureClassifier.isRetryable(failure)) {
          throw failure;
        }
        if (attempt == maxAttempts) {
          throw new RetryExhaustedException(attempt, failure);
        }
        var delay = delay(attempt);
        if (maxElapsed.compareTo(Duration.ZERO) > 0
            && Duration.between(startedAt, clock.instant()).plus(delay).compareTo(maxElapsed) > 0) {
          throw new RetryExhaustedException(
              attempt,
              new RetryExhaustedException.ElapsedBudgetExceeded(
                  Duration.between(startedAt, clock.instant()), failure));
        }
        sleeper.sleep(delay);
      }
    }
    throw new RetryExhaustedException(maxAttempts, lastFailure);
  }

  /** Returns the jittered delay before the next attempt. {@code attempt} starts at 1. */
  public Duration delay(int attempt) {
    if (attempt < 1) {
      attempt = 1;
    }
    if (initialDelay.isZero() || initialDelay.isNegative()) {
      return Duration.ZERO;
    }
    var delay = initialDelay;
    for (var i = 1; i < attempt && (maxDelay.isZero() || delay.compareTo(maxDelay) < 0); i++) {
      delay = delay.multipliedBy(2);
    }
    if (!maxDelay.isZero() && delay.compareTo(maxDelay) > 0) {
      delay = maxDelay;
    }
    return jitter.apply(delay);
  }

  private static void systemSleep(Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RetryInterruptedException(e);
    }
  }

  static Duration fullJitter(Duration delay) {
    if (delay.isZero() || delay.isNegative()) {
      return Duration.ZERO;
    }
    var upper = delay.toNanos();
    return Duration.ofNanos(ThreadLocalRandom.current().nextLong(upper + 1));
  }

  /** Signals that the policy stopped retrying. The last failure is the cause. */
  public static final class RetryExhaustedException extends RuntimeException {
    private final int attempts;

    RetryExhaustedException(int attempts, Throwable cause) {
      super("retry exhausted after " + attempts + " attempts", cause);
      this.attempts = attempts;
    }

    public int attempts() {
      return attempts;
    }

    /** Wraps the elapsed-budget check so the caller can distinguish it from the attempt cap. */
    public static final class ElapsedBudgetExceeded extends RuntimeException {
      private final Duration elapsed;

      ElapsedBudgetExceeded(Duration elapsed, Throwable cause) {
        super("retry elapsed budget exceeded after " + elapsed, cause);
        this.elapsed = elapsed;
      }

      public Duration elapsed() {
        return elapsed;
      }
    }
  }
}
