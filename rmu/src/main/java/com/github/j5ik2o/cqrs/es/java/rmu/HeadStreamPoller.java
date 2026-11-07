package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.ExpiredIteratorException;
import software.amazon.awssdk.services.dynamodb.model.TrimmedDataAccessException;

/**
 * Drives every shard of the head table's stream.
 *
 * <p>The first iterator starts at {@code TRIM_HORIZON} so a restart replays the retained records;
 * the read model positions drop what was already applied. An expired iterator is refreshed from the
 * last processed sequence number, which is not data loss. A trimmed shard raises {@link
 * StreamTrimmedException} so the caller runs a full resync (DY-15).
 */
public final class HeadStreamPoller {
  private static final Logger LOGGER = LoggerFactory.getLogger(HeadStreamPoller.class);

  @FunctionalInterface
  public interface StreamRecordHandler {
    void handle(DynamodbEvent.DynamodbStreamRecord record);
  }

  private final HeadStreamClient client;
  private final StreamRecordHandler handler;
  private final int maxItemCount;
  private final Map<String, String> iterators = new HashMap<>();
  private final Map<String, String> lastSequenceNumbers = new HashMap<>();
  private final Set<String> completedShards = new HashSet<>();
  private volatile Instant lastRecordTime;

  public HeadStreamPoller(HeadStreamClient client, StreamRecordHandler handler, int maxItemCount) {
    this.client = client;
    this.handler = handler;
    this.maxItemCount = maxItemCount;
  }

  /**
   * Polls each open shard once and returns the number of records handed to the handler. The caller
   * owns the idle or failure backoff between calls.
   */
  public synchronized int pollOnce(String headTableName) {
    var streamArn = client.streamArn(headTableName);
    var processed = 0;
    String lastEvaluatedShardId = null;
    do {
      var shardPage = client.describeShards(streamArn, lastEvaluatedShardId);
      for (var shardId : shardPage.shardIds()) {
        if (completedShards.contains(shardId)) {
          continue;
        }
        var iterator = iterators.get(shardId);
        if (iterator == null) {
          iterator = client.shardIteratorAtTrimHorizon(streamArn, shardId);
        }
        while (iterator != null) {
          StreamRecordPage page;
          try {
            page = client.getRecords(streamArn, iterator, maxItemCount);
          } catch (ExpiredIteratorException e) {
            LOGGER.warn(
                "Stream iterator expired; resuming after the last processed sequence number: "
                    + "shardId={}, sequenceNumber={}",
                shardId,
                lastSequenceNumbers.get(shardId));
            iterator = nextIteratorAfterLastSequence(streamArn, shardId);
            continue;
          } catch (TrimmedDataAccessException e) {
            throw new StreamTrimmedException(shardId, e);
          }
          for (var index = 0; index < page.records().size(); index++) {
            handler.handle(page.records().get(index));
            lastSequenceNumbers.put(shardId, page.sequenceNumbers().get(index));
            processed++;
          }
          if (!page.records().isEmpty()) {
            lastRecordTime = Instant.now();
          }
          var nextIterator = page.nextShardIterator();
          if (nextIterator == null) {
            iterators.remove(shardId);
            completedShards.add(shardId);
            break;
          }
          iterators.put(shardId, nextIterator);
          iterator = nextIterator;
          if (page.records().isEmpty()) {
            break;
          }
        }
      }
      lastEvaluatedShardId = shardPage.lastEvaluatedShardId();
    } while (lastEvaluatedShardId != null);
    return processed;
  }

  private String nextIteratorAfterLastSequence(String streamArn, String shardId) {
    var lastSequenceNumber = lastSequenceNumbers.get(shardId);
    if (lastSequenceNumber == null) {
      return client.shardIteratorAtTrimHorizon(streamArn, shardId);
    }
    return client.shardIteratorAfterSequenceNumber(streamArn, shardId, lastSequenceNumber);
  }

  /** Clears the iterator state before a full resync. */
  public synchronized void reset() {
    iterators.clear();
    lastSequenceNumbers.clear();
    completedShards.clear();
    lastRecordTime = null;
  }

  /** The time the last record was read, or null when no record has been read yet. */
  public Instant lastRecordTime() {
    return lastRecordTime;
  }
}
