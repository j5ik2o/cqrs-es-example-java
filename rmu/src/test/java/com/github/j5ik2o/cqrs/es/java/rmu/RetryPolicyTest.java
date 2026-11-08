package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  @Test
  void retriesUntilSuccess() {
    var sleeper = new RecordingSleeper();
    var policy = identityPolicy(5, Duration.ofSeconds(30), Duration.ofMillis(100), sleeper);
    var attempts = new AtomicInteger();

    policy.run(
        () -> {
          if (attempts.incrementAndGet() < 3) {
            throw new RuntimeException("transient");
          }
        });

    assertEquals(3, attempts.get());
    assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(200)), sleeper.delays);
  }

  @Test
  void stopsAtTheAttemptLimit() {
    var sleeper = new RecordingSleeper();
    var policy = identityPolicy(3, Duration.ofSeconds(30), Duration.ofMillis(100), sleeper);
    var attempts = new AtomicInteger();

    var failure =
        assertThrows(
            RetryPolicy.RetryExhaustedException.class,
            () ->
                policy.run(
                    () -> {
                      attempts.incrementAndGet();
                      throw new RuntimeException("still failing");
                    }));

    assertEquals(3, attempts.get());
    assertEquals(3, failure.attempts());
    assertEquals(2, sleeper.delays.size());
  }

  @Test
  void stopsWhenTheElapsedBudgetIsExhausted() {
    var sleeper = new RecordingSleeper();
    var policy =
        new RetryPolicy(
            10,
            Duration.ofMillis(250),
            Duration.ofMillis(100),
            Duration.ofSeconds(1),
            sleeper,
            delay -> delay,
            sleeper.clock);
    var attempts = new AtomicInteger();

    var failure =
        assertThrows(
            RetryPolicy.RetryExhaustedException.class,
            () ->
                policy.run(
                    () -> {
                      attempts.incrementAndGet();
                      throw new RuntimeException("slow storage");
                    }));

    assertEquals(2, attempts.get());
    assertInstanceOf(
        RetryPolicy.RetryExhaustedException.ElapsedBudgetExceeded.class, failure.getCause());
  }

  @Test
  void doesNotRetryAPermanentFailure() {
    var sleeper = new RecordingSleeper();
    var policy = identityPolicy(5, Duration.ofSeconds(30), Duration.ofMillis(100), sleeper);
    var failure = new PermanentFailure(FailureKind.EVENT_PAYLOAD, "bad payload");
    var attempts = new AtomicInteger();

    var thrown =
        assertThrows(
            PermanentFailure.class,
            () ->
                policy.run(
                    () -> {
                      attempts.incrementAndGet();
                      throw failure;
                    }));

    assertSame(failure, thrown);
    assertEquals(1, attempts.get());
    assertEquals(List.of(), sleeper.delays);
  }

  @Test
  void backoffGrowsCapsAndResets() {
    var backoff = new Backoff(Duration.ofMillis(100), Duration.ofMillis(300), delay -> delay);

    assertEquals(Duration.ofMillis(100), backoff.next());
    assertEquals(Duration.ofMillis(200), backoff.next());
    assertEquals(Duration.ofMillis(300), backoff.next());
    assertEquals(Duration.ofMillis(300), backoff.next());
    backoff.reset();
    assertEquals(Duration.ofMillis(100), backoff.next());
  }

  private static RetryPolicy identityPolicy(
      int maxAttempts, Duration maxElapsed, Duration initialDelay, RecordingSleeper sleeper) {
    return new RetryPolicy(
        maxAttempts,
        maxElapsed,
        initialDelay,
        Duration.ofSeconds(1),
        sleeper,
        delay -> delay,
        sleeper.clock);
  }

  private static final class RecordingSleeper implements Sleeper {
    private final MutableClock clock = new MutableClock();
    private final List<Duration> delays = new ArrayList<>();

    @Override
    public void sleep(Duration delay) {
      delays.add(delay);
      clock.advance(delay);
    }
  }

  private static final class MutableClock extends Clock {
    private Instant instant = Instant.EPOCH;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }
}
