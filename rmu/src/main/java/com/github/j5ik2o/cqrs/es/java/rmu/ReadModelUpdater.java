package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Builds the read model from the head table's DynamoDB Streams.
 *
 * <p>Each head stream record carries the head item's {@code events} list, which holds the events of
 * one write transaction. Duplicate and stale sequence numbers are dropped, a gap in {@code seq_nr}
 * is filled from the journal, and the aggregate's position is written in the same database
 * transaction as the read model change.
 *
 * <p>A retryable failure is retried with exponential backoff. A permanent failure, or a retry that
 * exhausted its budget, is recorded as a recovery task and the aggregate is blocked for the rest of
 * the batch so the position cannot advance past an unapplied event.
 */
@Component
@Profile("local-rmu")
public class ReadModelUpdater {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReadModelUpdater.class);
  private static final String CONFIG_AGGREGATE_ID = "__config__";
  private static final String EVENT_NAME_INSERT = "INSERT";
  private static final String EVENT_NAME_MODIFY = "MODIFY";
  private static final String EVENT_NAME_REMOVE = "REMOVE";

  private final EventApplier applier;
  private final RecoveryService recovery;
  private final RetryPolicy retryPolicy;

  /** Convenience constructor for tests and callers that do not need recovery. */
  public ReadModelUpdater(SqlSessionFactory sqlSessionFactory, JournalReader journalReader) {
    this(new Applier(sqlSessionFactory, journalReader, 1), null, RetryPolicy.noRetry());
  }

  /**
   * Lambda entry point. Lambda instantiates the handler class with a no-argument constructor, so
   * this constructor builds the dependencies from environment variables.
   */
  public ReadModelUpdater() {
    var dependencies = LambdaRmuDependencies.create();
    this.applier = dependencies.applier();
    this.recovery = dependencies.recoveryService();
    this.retryPolicy = dependencies.retryPolicy();
  }

  @Autowired
  public ReadModelUpdater(EventApplier applier, RecoveryService recovery, RetryPolicy retryPolicy) {
    this.applier = applier;
    this.recovery = recovery;
    this.retryPolicy = retryPolicy;
  }

  /** Applies every record in order and stops at the first unhandled failure. */
  public void update(DynamodbEvent dynamodbEvent) {
    for (var record : dynamodbEvent.getRecords()) {
      processRecord(record);
    }
  }

  /** Applies one record. The local stream reader calls this per record. */
  public void updateRecord(DynamodbEvent.DynamodbStreamRecord record) {
    processRecord(record);
  }

  /**
   * The Lambda entry point. It returns the records that must be redelivered as a partial batch
   * response.
   *
   * <p>Records of an aggregate whose earlier event failed are left for redelivery: the position
   * rule forbids advancing past an unapplied event, so applying them would only fail again. A
   * failure that was recorded as a recovery task is not redelivered; the durable task owns it from
   * then on. If recording the task itself fails, the record is redelivered so the Lambda retry and
   * OnFailure safety net still apply.
   */
  public DynamodbBatchResponse handleRequest(DynamodbEvent dynamodbEvent, Context context) {
    var failures = new ArrayList<DynamodbBatchResponse.BatchItemFailure>();
    var blockedAggregates = new HashSet<String>();
    for (var record : dynamodbEvent.getRecords()) {
      var aggregateId = aggregateIdOf(record);
      if (aggregateId != null && blockedAggregates.contains(aggregateId)) {
        failures.add(new DynamodbBatchResponse.BatchItemFailure(itemIdentifier(record)));
        continue;
      }
      try {
        var result = processRecord(record);
        if (result == RecordResult.RECORDED_FAILURE && aggregateId != null) {
          blockedAggregates.add(aggregateId);
        }
      } catch (RuntimeException e) {
        LOGGER.error(
            "Failed to apply head stream record: eventId={}, error={}",
            record.getEventID(),
            e.getMessage(),
            e);
        failures.add(new DynamodbBatchResponse.BatchItemFailure(itemIdentifier(record)));
        if (aggregateId != null) {
          blockedAggregates.add(aggregateId);
        }
      }
    }
    return new DynamodbBatchResponse(List.copyOf(failures));
  }

  private RecordResult processRecord(DynamodbEvent.DynamodbStreamRecord record) {
    RecordIdentity identity;
    try {
      identity = parseHeadRecord(record);
    } catch (RuntimeException e) {
      var fallback = fallbackIdentity(record);
      if (recovery != null && fallback != null && isRecoverable(e)) {
        LOGGER.error(
            "Failed to parse a head record; recorded a recovery task: aid={}, seqNr={}",
            fallback.aggregateId(),
            fallback.headSeqNr(),
            e);
        recovery.record(fallback.aggregateId(), fallback.headSeqNr(), fallback.headSeqNr(), e);
        return RecordResult.RECORDED_FAILURE;
      }
      throw e;
    }
    if (identity.skip()) {
      return RecordResult.APPLIED;
    }
    var lastSeqNr = identity.events().get(identity.events().size() - 1).seqNr();
    for (var headEvent : identity.events()) {
      try {
        retryPolicy.run(() -> applier.applyHeadEvent(identity.aggregateId(), headEvent));
      } catch (RuntimeException e) {
        if (recovery == null || !isRecoverable(e)) {
          throw e;
        }
        // A record can carry several events. Recovery must reach the record's last event,
        // otherwise the events after the failed one wait for another record or a resync.
        recovery.record(identity.aggregateId(), lastSeqNr, headEvent.seqNr(), e);
        return RecordResult.RECORDED_FAILURE;
      }
    }
    return RecordResult.APPLIED;
  }

  private static HeadIdentity fallbackIdentity(DynamodbEvent.DynamodbStreamRecord record) {
    try {
      var streamRecord = record.getDynamodb();
      if (streamRecord == null || streamRecord.getNewImage() == null) {
        return null;
      }
      var image = streamRecord.getNewImage();
      var aggregateId = getStringAttribute(image, "aid");
      if (!AggregateType.isGroupChat(aggregateId)) {
        return null;
      }
      return new HeadIdentity(aggregateId, getNumberAttribute(image, "seq_nr"));
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static boolean isRecoverable(Throwable failure) {
    return FailureClassifier.classify(failure).isPresent()
        || failure instanceof RetryPolicy.RetryExhaustedException;
  }

  private static RecordIdentity parseHeadRecord(DynamodbEvent.DynamodbStreamRecord record) {
    var eventName = record.getEventName();
    if (EVENT_NAME_REMOVE.equals(eventName)) {
      return RecordIdentity.skipped();
    }
    if (!EVENT_NAME_INSERT.equals(eventName) && !EVENT_NAME_MODIFY.equals(eventName)) {
      throw PermanentFailure.permanent(
          FailureKind.EVENT_PAYLOAD, "unexpected stream event name " + eventName);
    }
    var image = getNewImage(record);
    var aggregateId = getStringAttribute(image, "aid");
    if (CONFIG_AGGREGATE_ID.equals(aggregateId)) {
      return RecordIdentity.skipped();
    }
    if (!AggregateType.isGroupChat(aggregateId)) {
      LOGGER.debug("Skipping head record of another aggregate type: aid={}", aggregateId);
      return RecordIdentity.skipped();
    }
    var events = parseHeadEvents(image);
    if (events.isEmpty()) {
      throw PermanentFailure.permanent(FailureKind.EVENT_PAYLOAD, "head record has no events");
    }
    return new RecordIdentity(false, aggregateId, events);
  }

  private static String aggregateIdOf(DynamodbEvent.DynamodbStreamRecord record) {
    try {
      var streamRecord = record.getDynamodb();
      if (streamRecord == null || streamRecord.getNewImage() == null) {
        return null;
      }
      var aggregateId = getStringAttribute(streamRecord.getNewImage(), "aid");
      if (CONFIG_AGGREGATE_ID.equals(aggregateId) || !AggregateType.isGroupChat(aggregateId)) {
        return null;
      }
      return aggregateId;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * DynamoDB batch item failures are identified by the sequence number, not the event id. Without a
   * sequence number Lambda cannot check point the batch, so the whole invocation fails and the
   * batch is retried instead of silently dropping the record.
   */
  private static String itemIdentifier(DynamodbEvent.DynamodbStreamRecord record) {
    var streamRecord = record.getDynamodb();
    if (streamRecord != null && streamRecord.getSequenceNumber() != null) {
      return streamRecord.getSequenceNumber();
    }
    throw PermanentFailure.permanent(
        FailureKind.EVENT_PAYLOAD, "stream record has no sequence number");
  }

  private static List<HeadEvent> parseHeadEvents(Map<String, AttributeValue> image) {
    var eventsAttribute = image.get("events");
    if (eventsAttribute == null || eventsAttribute.getL() == null) {
      throw PermanentFailure.permanent(
          FailureKind.EVENT_PAYLOAD, "head image has no events attribute");
    }
    var events = new ArrayList<HeadEvent>();
    for (var item : eventsAttribute.getL()) {
      var fields = item.getM();
      if (fields == null) {
        throw PermanentFailure.permanent(FailureKind.EVENT_PAYLOAD, "head event is not a map");
      }
      events.add(
          new HeadEvent(
              getNumberAttribute(fields, "seq_nr"),
              Instant.ofEpochSecond(0, getNumberAttribute(fields, "occurred_at")),
              getOptionalStringAttribute(fields, "manifest"),
              getPayload(fields)));
    }
    events.sort(Comparator.comparingLong(HeadEvent::seqNr));
    return events;
  }

  private static Map<String, AttributeValue> getNewImage(
      DynamodbEvent.DynamodbStreamRecord record) {
    var streamRecord = record.getDynamodb();
    if (streamRecord == null) {
      throw PermanentFailure.permanent(FailureKind.EVENT_PAYLOAD, "streamRecord is null");
    }
    var attributeValues = streamRecord.getNewImage();
    if (attributeValues == null) {
      throw PermanentFailure.permanent(FailureKind.EVENT_PAYLOAD, "new image is null");
    }
    return attributeValues;
  }

  private static String getStringAttribute(Map<String, AttributeValue> attributes, String name) {
    var attribute = attributes.get(name);
    if (attribute == null || attribute.getS() == null) {
      throw PermanentFailure.permanent(
          FailureKind.EVENT_PAYLOAD, "attribute " + name + " is missing or not a string");
    }
    return attribute.getS();
  }

  private static String getOptionalStringAttribute(
      Map<String, AttributeValue> attributes, String name) {
    var attribute = attributes.get(name);
    return attribute == null || attribute.getS() == null ? "" : attribute.getS();
  }

  private static long getNumberAttribute(Map<String, AttributeValue> attributes, String name) {
    var attribute = attributes.get(name);
    if (attribute == null || attribute.getN() == null) {
      throw PermanentFailure.permanent(
          FailureKind.EVENT_PAYLOAD, "attribute " + name + " is missing or not a number");
    }
    return Long.parseLong(attribute.getN());
  }

  private static byte[] getPayload(Map<String, AttributeValue> attributes) {
    var attribute = attributes.get("payload");
    if (attribute == null) {
      throw PermanentFailure.permanent(FailureKind.EVENT_PAYLOAD, "head event has no payload");
    }
    if (attribute.getB() != null) {
      ByteBuffer buffer = attribute.getB().duplicate();
      var bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      return bytes;
    }
    if (attribute.getS() != null) {
      return attribute.getS().getBytes(StandardCharsets.UTF_8);
    }
    throw PermanentFailure.permanent(FailureKind.EVENT_PAYLOAD, "head event has no payload");
  }

  private enum RecordResult {
    APPLIED,
    RECORDED_FAILURE
  }

  private record RecordIdentity(boolean skip, String aggregateId, List<HeadEvent> events) {
    static RecordIdentity skipped() {
      return new RecordIdentity(true, null, List.of());
    }
  }

  private record HeadIdentity(String aggregateId, long headSeqNr) {}
}
