package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays durable recovery tasks when the recovery queue wakes it.
 *
 * <p>The SQS message is only a wake-up signal. The worker reads pending tasks from MySQL and
 * replays them from the journal, so a redelivered message is harmless. If a run leaves tasks
 * pending, every message is returned as a batch item failure so SQS retries and the queue's redrive
 * policy eventually parks a poison message.
 */
public final class RecoveryWorker {
  private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryWorker.class);
  // A single message may wake a large backlog. Drain it in bounded batches, then let SQS
  // redeliver if the safety cap is reached.
  private static final int MAX_BATCHES_PER_RUN = 10;

  private final RecoveryService recoveryService;
  private final int maxTasksPerRun;

  /** Lambda instantiates the handler class with a no-argument constructor. */
  public RecoveryWorker() {
    var dependencies = LambdaRmuDependencies.create();
    this.recoveryService = dependencies.recoveryService();
    this.maxTasksPerRun = dependencies.maxRecoveryTasksPerRun();
  }

  RecoveryWorker(RecoveryService recoveryService, int maxTasksPerRun) {
    this.recoveryService = recoveryService;
    this.maxTasksPerRun = maxTasksPerRun;
  }

  public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
    RecoveryRun run;
    try {
      run = recoverAllBatches();
    } catch (RuntimeException e) {
      return failureResponse(event, e);
    }
    LOGGER.info("Recovery run completed {} pending tasks", run.completed());
    if (!run.capped()) {
      return new SQSBatchResponse(List.of());
    }
    return failureResponse(event, new RecoveryException("recovery run reached the batch cap"));
  }

  private RecoveryRun recoverAllBatches() {
    var total = 0;
    for (var batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
      var completed = recoveryService.recoverPending(maxTasksPerRun);
      total += completed;
      if (completed < maxTasksPerRun) {
        return new RecoveryRun(total, false);
      }
    }
    return new RecoveryRun(total, true);
  }

  private static SQSBatchResponse failureResponse(SQSEvent event, RuntimeException failure) {
    var failures = redeliverAll(event);
    if (failures.isEmpty()) {
      // A manual invocation has no SQS message to redeliver, so the caller must see the failure
      // instead of a successful response.
      throw failure;
    }
    LOGGER.error("Recovery run failed; the messages will be redelivered", failure);
    return new SQSBatchResponse(failures);
  }

  private static List<SQSBatchResponse.BatchItemFailure> redeliverAll(SQSEvent event) {
    if (event == null || event.getRecords() == null) {
      return List.of();
    }
    return event.getRecords().stream()
        .map(SQSEvent.SQSMessage::getMessageId)
        .filter(Objects::nonNull)
        .map(SQSBatchResponse.BatchItemFailure::new)
        .toList();
  }

  private record RecoveryRun(int completed, boolean capped) {}
}
