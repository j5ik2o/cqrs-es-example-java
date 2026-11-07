package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecoveryWorkerTest {

  @Test
  void deletesEveryMessageWhenRecoveryCompletes() {
    var store = new RmuTestSupport.InMemoryRecoveryTaskStore();
    var recovery =
        new RecoveryService(store, () -> RmuTestSupport.STORE_ID, new NoopApplier(), null);
    var worker = new RecoveryWorker(recovery, 50);

    var response = worker.handleRequest(sqsEvent("message-1", "message-2"), null);

    assertTrue(response.getBatchItemFailures().isEmpty());
  }

  @Test
  void redeliversEveryMessageWhenRecoveryLeavesTasksPending() {
    var store = new RmuTestSupport.InMemoryRecoveryTaskStore();
    store.record(task("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z"));
    var recovery =
        new RecoveryService(store, () -> RmuTestSupport.STORE_ID, new NoopApplier(), null);
    var worker = new RecoveryWorker(recovery, 50);

    var response = worker.handleRequest(sqsEvent("message-1", "message-2"), null);

    assertEquals(
        List.of("message-1", "message-2"),
        response.getBatchItemFailures().stream()
            .map(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
            .toList());
  }

  @Test
  void surfacesTheFailureWhenAManualInvocationHasNoMessages() {
    var store = new RmuTestSupport.InMemoryRecoveryTaskStore();
    store.record(task("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z"));
    var recovery =
        new RecoveryService(store, () -> RmuTestSupport.STORE_ID, new NoopApplier(), null);
    var worker = new RecoveryWorker(recovery, 50);

    assertThrows(RecoveryException.class, () -> worker.handleRequest(new SQSEvent(), null));
  }

  @Test
  void drainsMoreTasksThanOneBatchWithoutRedelivery() {
    var store = new RmuTestSupport.InMemoryRecoveryTaskStore();
    for (var index = 0; index < 3; index++) {
      store.record(task("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0" + index));
    }
    var recovery =
        new RecoveryService(store, () -> RmuTestSupport.STORE_ID, new AdvancingApplier(), null);
    var worker = new RecoveryWorker(recovery, 1);

    var response = worker.handleRequest(sqsEvent("message-1"), null);

    assertTrue(response.getBatchItemFailures().isEmpty());
    assertTrue(store.listPending().isEmpty());
  }

  @Test
  void redeliversWhenTheSafetyCapIsReached() {
    var store = new RmuTestSupport.InMemoryRecoveryTaskStore();
    for (var index = 0; index < 11; index++) {
      store.record(task("GroupChat-01H42K4ABWQ5V2XQEP3A48VE" + String.format("%02d", index)));
    }
    var recovery =
        new RecoveryService(store, () -> RmuTestSupport.STORE_ID, new AdvancingApplier(), null);
    var worker = new RecoveryWorker(recovery, 1);

    var response = worker.handleRequest(sqsEvent("message-1"), null);

    assertEquals(
        List.of("message-1"),
        response.getBatchItemFailures().stream()
            .map(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
            .toList());
    assertEquals(1, store.listPending().size());
  }

  private static RecoveryTask task(String aggregateId) {
    return new RecoveryTask(
        RmuTestSupport.STORE_ID,
        Projection.NAME,
        Projection.VERSION,
        aggregateId,
        1,
        FailureKind.EVENT_PAYLOAD,
        "test",
        RecoveryTask.STATUS_PENDING,
        null,
        null);
  }

  private static SQSEvent sqsEvent(String... messageIds) {
    var messages = new ArrayList<SQSEvent.SQSMessage>();
    for (var messageId : messageIds) {
      var message = new SQSEvent.SQSMessage();
      message.setMessageId(messageId);
      messages.add(message);
    }
    var event = new SQSEvent();
    event.setRecords(messages);
    return event;
  }

  private static final class NoopApplier implements EventApplier {
    @Override
    public long position(String aggregateId) {
      return 0L;
    }

    @Override
    public void applyHeadEvent(String aggregateId, HeadEvent event) {}

    @Override
    public void applyThrough(String aggregateId, long targetSeqNr) {}
  }

  private static final class AdvancingApplier implements EventApplier {
    private final Map<String, Long> positions = new HashMap<>();

    @Override
    public long position(String aggregateId) {
      return positions.getOrDefault(aggregateId, 0L);
    }

    @Override
    public void applyHeadEvent(String aggregateId, HeadEvent event) {
      positions.put(aggregateId, event.seqNr());
    }

    @Override
    public void applyThrough(String aggregateId, long targetSeqNr) {
      positions.put(aggregateId, targetSeqNr);
    }
  }
}
