package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadModelUpdaterHandlerTest {

  @Test
  void redeliversOnlyTheBlockedRecordsOfAFailedAggregate() {
    var failedAggregate = "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0A";
    var healthyAggregate = "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0B";
    var applier = new RejectingApplier(failedAggregate);
    var store = new RmuTestSupport.InMemoryRecoveryTaskStore();
    var recovery = new RecoveryService(store, () -> "store-0001", applier, null);
    var updater = new ReadModelUpdater(applier, recovery, RetryPolicy.noRetry());

    var event = new DynamodbEvent();
    event.setRecords(
        List.of(
            record(failedAggregate, 1, "sequence-a-1"),
            record(failedAggregate, 2, "sequence-a-2"),
            record(healthyAggregate, 1, "sequence-b-1")));

    var response = updater.handleRequest(event, null);

    assertEquals(
        List.of("sequence-a-2"),
        response.batchItemFailures().stream()
            .map(DynamodbBatchResponse.BatchItemFailure::itemIdentifier)
            .toList());
    assertEquals(List.of(healthyAggregate), applier.applied);
    assertEquals(1, store.listPending().size());
    assertEquals(failedAggregate, store.listPending().get(0).aggregateId());
    assertEquals(1L, store.listPending().get(0).seqNr());
  }

  private static DynamodbEvent.DynamodbStreamRecord record(
      String aggregateId, long seqNr, String sequenceNumber) {
    var fixture = RmuTestSupport.createFixture();
    var event =
        RmuTestSupport.headRecord(
            aggregateId, "MODIFY", List.of(RmuTestSupport.headEvent(seqNr, fixture.created())));
    var record = event.getRecords().get(0);
    record.getDynamodb().setSequenceNumber(sequenceNumber);
    return record;
  }

  private static final class RejectingApplier implements EventApplier {
    private final String rejectedAggregateId;
    private final List<String> applied = new ArrayList<>();

    private RejectingApplier(String rejectedAggregateId) {
      this.rejectedAggregateId = rejectedAggregateId;
    }

    @Override
    public long position(String aggregateId) {
      return 0L;
    }

    @Override
    public void applyHeadEvent(String aggregateId, HeadEvent event) {
      if (rejectedAggregateId.equals(aggregateId)) {
        throw new PermanentFailure(FailureKind.EVENT_PAYLOAD, "bad payload");
      }
      applied.add(aggregateId);
    }

    @Override
    public void applyThrough(String aggregateId, long targetSeqNr) {}
  }
}
