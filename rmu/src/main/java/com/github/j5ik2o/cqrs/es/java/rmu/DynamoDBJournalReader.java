package com.github.j5ik2o.cqrs.es.java.rmu;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Reads journal events with a strongly consistent query, as DY-11 requires. The reader is used only
 * when a head stream record skipped sequence numbers.
 */
public final class DynamoDBJournalReader implements JournalReader {
  private final DynamoDbClient client;
  private final String journalTableName;

  public DynamoDBJournalReader(DynamoDbClient client, String journalTableName) {
    this.client = client;
    this.journalTableName = journalTableName;
  }

  @Override
  public List<HeadEvent> readEvents(String aggregateId, long fromSeqNr, long toSeqNr) {
    if (fromSeqNr > toSeqNr) {
      return List.of();
    }
    var request =
        QueryRequest.builder()
            .tableName(journalTableName)
            .consistentRead(true)
            .scanIndexForward(true)
            .keyConditionExpression("aid = :aid AND seq_nr BETWEEN :from_seq_nr AND :to_seq_nr")
            .expressionAttributeValues(
                Map.of(
                    ":aid", AttributeValue.fromS(aggregateId),
                    ":from_seq_nr", AttributeValue.fromN(Long.toString(fromSeqNr)),
                    ":to_seq_nr", AttributeValue.fromN(Long.toString(toSeqNr))))
            .build();

    var events = new ArrayList<HeadEvent>();
    QueryResponse response;
    do {
      response = client.query(request);
      for (var item : response.items()) {
        events.add(toHeadEvent(aggregateId, item));
      }
      request = request.toBuilder().exclusiveStartKey(response.lastEvaluatedKey()).build();
    } while (response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty());
    return events;
  }

  private static HeadEvent toHeadEvent(String aggregateId, Map<String, AttributeValue> item) {
    var seqNr = numberAttribute(item, "seq_nr");
    var occurredAtNanos = numberAttribute(item, "occurred_at");
    var manifestAttribute = item.get("manifest");
    var manifest =
        manifestAttribute == null || manifestAttribute.s() == null ? "" : manifestAttribute.s();
    return new HeadEvent(
        seqNr,
        Instant.ofEpochSecond(0, occurredAtNanos),
        manifest,
        payloadBytes(aggregateId, item.get("payload")));
  }

  private static long numberAttribute(Map<String, AttributeValue> item, String name) {
    var attribute = item.get(name);
    if (attribute == null || attribute.n() == null) {
      throw new ReadModelUpdateException("journal item has no numeric " + name);
    }
    return Long.parseLong(attribute.n());
  }

  private static byte[] payloadBytes(String aggregateId, AttributeValue payload) {
    if (payload == null) {
      throw new ReadModelUpdateException("journal item for " + aggregateId + " has no payload");
    }
    if (payload.b() != null) {
      var buffer = payload.b().asByteBuffer().duplicate();
      var bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      return bytes;
    }
    if (payload.s() != null) {
      return payload.s().getBytes(StandardCharsets.UTF_8);
    }
    throw new ReadModelUpdateException("journal item for " + aggregateId + " has no payload");
  }
}
