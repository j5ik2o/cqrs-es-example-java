package com.github.j5ik2o.cqrs.es.java.rmu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements DY-15's reconciliation: scan every head, compare its seq_nr with the projection
 * position, and fill the lag from the journal with the same position guard as the stream path.
 *
 * <p>A permanent per-aggregate failure is recorded as a recovery task and does not stop the
 * remaining aggregates. A storage failure aborts the scan so the caller can retry it.
 */
public final class Resyncer {
  private static final Logger LOGGER = LoggerFactory.getLogger(Resyncer.class);

  private final EventApplier applier;
  private final HeadScanner scanner;
  private final RecoveryService recovery;

  public Resyncer(EventApplier applier, HeadScanner scanner, RecoveryService recovery) {
    this.applier = applier;
    this.scanner = scanner;
    this.recovery = recovery;
  }

  public ResyncResult resync() {
    var heads = new int[1];
    var skipped = new int[1];
    var advanced = new int[1];
    var failed = new int[1];
    RuntimeException[] recordedFailure = new RuntimeException[1];

    scanner.scanHeads(
        head -> {
          if (!AggregateType.isGroupChat(head.aggregateId())) {
            skipped[0]++;
            return;
          }
          heads[0]++;
          var position = applier.position(head.aggregateId());
          if (head.seqNr() <= position) {
            return;
          }
          advanced[0]++;
          try {
            applier.applyThrough(head.aggregateId(), head.seqNr());
          } catch (RuntimeException e) {
            if (!isRecoverable(e)) {
              throw e;
            }
            failed[0]++;
            if (recovery != null) {
              try {
                recovery.record(head.aggregateId(), head.seqNr(), e);
              } catch (RuntimeException recordError) {
                recordError.addSuppressed(e);
                if (recordedFailure[0] == null) {
                  recordedFailure[0] = recordError;
                } else {
                  recordedFailure[0].addSuppressed(recordError);
                }
                return;
              }
            }
            LOGGER.error(
                "Resync could not apply an aggregate; recorded a recovery task: aid={}, seqNr={}",
                head.aggregateId(),
                head.seqNr(),
                e);
          }
        });

    if (recordedFailure[0] != null) {
      throw recordedFailure[0];
    }
    return new ResyncResult(heads[0], skipped[0], advanced[0], failed[0]);
  }

  private static boolean isRecoverable(Throwable failure) {
    return FailureClassifier.classify(failure).isPresent()
        || failure instanceof RetryPolicy.RetryExhaustedException;
  }
}
