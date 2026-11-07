package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ExpiredIteratorException;
import software.amazon.awssdk.services.dynamodb.model.TrimmedDataAccessException;

class HeadStreamPollerTest {

  @Test
  void resumesAnExpiredIteratorAfterTheLastSequenceNumber() {
    var client = new FakeHeadStreamClient();
    client.responses.add(page(record("event-1"), "1", "iterator-1"));
    client.responses.add(ExpiredIteratorException.builder().message("expired").build());
    client.responses.add(page(record("event-2"), "2", "iterator-2"));
    client.responses.add(page(null, null, "iterator-2"));
    var handled = new ArrayList<String>();
    var poller = new HeadStreamPoller(client, record -> handled.add(record.getEventID()), 10);

    var processed = poller.pollOnce("head");

    assertEquals(2, processed);
    assertEquals(List.of("event-1", "event-2"), handled);
    assertEquals(List.of("1"), client.afterSequenceCalls);
  }

  @Test
  void signalsAFullResyncWhenTheStreamWasTrimmed() {
    var client = new FakeHeadStreamClient();
    client.responses.add(page(record("event-1"), "1", "iterator-1"));
    client.responses.add(TrimmedDataAccessException.builder().message("trimmed").build());
    var handled = new ArrayList<String>();
    var poller = new HeadStreamPoller(client, record -> handled.add(record.getEventID()), 10);

    var failure = assertThrows(StreamTrimmedException.class, () -> poller.pollOnce("head"));

    assertEquals("shard-1", failure.shardId());
    assertEquals(List.of("event-1"), handled);
  }

  @Test
  void marksAClosedShardComplete() {
    var client = new FakeHeadStreamClient();
    client.responses.add(page(record("event-1"), "1", null));
    var handled = new ArrayList<String>();
    var poller = new HeadStreamPoller(client, record -> handled.add(record.getEventID()), 10);

    assertEquals(1, poller.pollOnce("head"));
    assertEquals(0, poller.pollOnce("head"));
    assertEquals(List.of("event-1"), handled);
  }

  private static DynamodbEvent.DynamodbStreamRecord record(String eventId) {
    var record = new DynamodbEvent.DynamodbStreamRecord();
    record.setEventID(eventId);
    record.setEventName("MODIFY");
    return record;
  }

  private static StreamRecordPage page(
      DynamodbEvent.DynamodbStreamRecord record, String sequenceNumber, String nextIterator) {
    if (record == null) {
      return new StreamRecordPage(List.of(), List.of(), nextIterator);
    }
    return new StreamRecordPage(List.of(record), List.of(sequenceNumber), nextIterator);
  }

  private static final class FakeHeadStreamClient implements HeadStreamClient {
    private final Deque<Object> responses = new ArrayDeque<>();
    private final List<String> afterSequenceCalls = new ArrayList<>();

    @Override
    public String streamArn(String headTableName) {
      return "stream-arn";
    }

    @Override
    public ShardPage describeShards(String streamArn, String exclusiveStartShardId) {
      return new ShardPage(List.of("shard-1"), null);
    }

    @Override
    public StreamRecordPage getRecords(String streamArn, String shardIterator, int limit) {
      var response = responses.removeFirst();
      if (response instanceof RuntimeException exception) {
        throw exception;
      }
      return (StreamRecordPage) response;
    }

    @Override
    public String shardIteratorAtTrimHorizon(String streamArn, String shardId) {
      return "trim-horizon-iterator";
    }

    @Override
    public String shardIteratorAfterSequenceNumber(
        String streamArn, String shardId, String sequenceNumber) {
      afterSequenceCalls.add(sequenceNumber);
      return "after-sequence-iterator";
    }
  }
}
