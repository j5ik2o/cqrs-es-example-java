package com.github.j5ik2o.cqrs.es.java.main;

import com.github.j5ik2o.cqrs.es.java.rmu.Backoff;
import com.github.j5ik2o.cqrs.es.java.rmu.HeadStreamPoller;
import com.github.j5ik2o.cqrs.es.java.rmu.RecoveryService;
import com.github.j5ik2o.cqrs.es.java.rmu.Resyncer;
import com.github.j5ik2o.cqrs.es.java.rmu.RetryInterruptedException;
import com.github.j5ik2o.cqrs.es.java.rmu.RetryPolicy;
import com.github.j5ik2o.cqrs.es.java.rmu.StreamTrimmedException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The local read model updater.
 *
 * <p>It reconciles every head on startup, then follows the head Streams. A trimmed stream, or a
 * reader that has been stopped longer than the 24-hour retention window, triggers another full
 * resync (DY-15). Pending recovery tasks are replayed at startup and periodically afterwards.
 */
@Component
@Profile("local-rmu")
public class LocalRMUMain implements CommandLineRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalRMUMain.class);
  private static final Duration STREAM_RETENTION = Duration.ofHours(24);
  private static final Duration RECOVERY_INTERVAL = Duration.ofMinutes(1);

  private final AppConfig appConfig;
  private final HeadStreamPoller poller;
  private final Resyncer resyncer;
  private final RecoveryService recoveryService;
  private final RetryPolicy retryPolicy;
  private final Backoff pollBackoff;

  public LocalRMUMain(
      AppConfig appConfig,
      HeadStreamPoller poller,
      Resyncer resyncer,
      RecoveryService recoveryService,
      RetryPolicy retryPolicy,
      Backoff pollBackoff) {
    this.appConfig = appConfig;
    this.poller = poller;
    this.resyncer = resyncer;
    this.recoveryService = recoveryService;
    this.retryPolicy = retryPolicy;
    this.pollBackoff = pollBackoff;
  }

  @Override
  public void run(String... args) throws Exception {
    LOGGER.info("appConfig = {}", appConfig);
    var headTableName = appConfig.getStream().getHeadTableName();

    // The first start has no positions at all, and a restart may follow a stop longer than the
    // stream retention window. Reconcile every head before consuming the stream; the stream then
    // covers the gap between the scan and the first read.
    resync("initial reconciliation");
    recoverPending();
    var nextRecoveryAt = Instant.now().plus(RECOVERY_INTERVAL);

    while (true) {
      try {
        var lastRecordTime = poller.lastRecordTime();
        if (lastRecordTime != null
            && lastRecordTime.isBefore(Instant.now().minus(STREAM_RETENTION))) {
          LOGGER.warn(
              "The reader was stopped longer than the stream retention window; running a full resync");
          resync("resync after the retention window");
          poller.reset();
          pollBackoff.reset();
        }
        var processed = poller.pollOnce(headTableName);
        if (processed > 0) {
          pollBackoff.reset();
        } else {
          sleep(pollBackoff.next());
        }
        if (Instant.now().isAfter(nextRecoveryAt)) {
          recoverPending();
          nextRecoveryAt = Instant.now().plus(RECOVERY_INTERVAL);
        }
      } catch (StreamTrimmedException e) {
        LOGGER.warn(
            "The reader fell behind the stream retention window; running a full resync: shardId={}",
            e.shardId(),
            e);
        resync("resync after trimmed stream");
        poller.reset();
        pollBackoff.reset();
      } catch (RetryInterruptedException e) {
        throw e;
      } catch (RuntimeException e) {
        LOGGER.warn(
            "An error has occurred, but stream processing is restarted. "
                + "If this error persists, the read model condition may be incorrect.",
            e);
        sleep(pollBackoff.next());
      }
    }
  }

  private void resync(String label) {
    try {
      retryPolicy.run(
          () -> {
            var result = resyncer.resync();
            LOGGER.info(
                "{} finished: heads={}, skipped={}, advanced={}, failed={}",
                label,
                result.heads(),
                result.skipped(),
                result.advanced(),
                result.failed());
          });
    } catch (RetryInterruptedException e) {
      throw e;
    } catch (RuntimeException e) {
      // Permanent per-aggregate failures were already recorded as recovery tasks and logged
      // inside the resyncer. A storage failure is retried on the next loop.
      LOGGER.error("{} did not complete", label, e);
    }
  }

  private void recoverPending() {
    try {
      var completed = recoveryService.recoverPending();
      if (completed > 0) {
        LOGGER.info("Completed pending recovery tasks: count={}", completed);
      }
    } catch (RetryInterruptedException e) {
      throw e;
    } catch (RuntimeException e) {
      LOGGER.warn("Some pending recovery tasks did not complete", e);
    }
  }

  private static void sleep(Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RetryInterruptedException(e);
    }
  }
}
