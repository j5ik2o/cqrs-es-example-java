package com.github.j5ik2o.cqrs.es.java.rmu;

import java.util.Map;
import java.util.function.Consumer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/**
 * Scans the head table with strong consistency for DY-15 resynchronisation.
 *
 * <p>The configuration item is filtered out of the scan, and the store identifier is read
 * separately from {@code __config__} so a recovery task cannot be replayed against another store's
 * journal.
 */
public final class DynamoDBHeadScanner implements HeadScanner, StoreIdProvider {
  static final String CONFIG_AGGREGATE_ID = "__config__";

  private final DynamoDbClient client;
  private final String tableName;
  private final Integer pageLimit;
  private volatile String storeId;

  public DynamoDBHeadScanner(DynamoDbClient client, String tableName) {
    this(client, tableName, null);
  }

  /** A page limit is used by tests to force pagination; null lets DynamoDB choose. */
  public DynamoDBHeadScanner(DynamoDbClient client, String tableName, Integer pageLimit) {
    this.client = client;
    this.tableName = tableName;
    this.pageLimit = pageLimit;
  }

  @Override
  public void scanHeads(Consumer<Head> visitor) {
    var builder =
        ScanRequest.builder()
            .tableName(tableName)
            .consistentRead(true)
            .projectionExpression("aid, seq_nr, type_name")
            .filterExpression("aid <> :config")
            .expressionAttributeValues(
                Map.of(":config", AttributeValue.fromS(CONFIG_AGGREGATE_ID)));
    if (pageLimit != null) {
      builder = builder.limit(pageLimit);
    }
    var request = builder.build();
    while (true) {
      var response = client.scan(request);
      for (var item : response.items()) {
        visitor.accept(toHead(item));
      }
      var lastEvaluatedKey = response.lastEvaluatedKey();
      if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
        return;
      }
      request = request.toBuilder().exclusiveStartKey(lastEvaluatedKey).build();
    }
  }

  @Override
  public String storeId() {
    var cached = storeId;
    if (cached != null) {
      return cached;
    }
    var response =
        client.getItem(
            GetItemRequest.builder()
                .tableName(tableName)
                .consistentRead(true)
                .key(Map.of("aid", AttributeValue.fromS(CONFIG_AGGREGATE_ID)))
                .build());
    var item = response.item();
    if (item == null || item.get("store_id") == null || item.get("store_id").s() == null) {
      throw new ReadModelUpdateException(
          "head configuration item " + CONFIG_AGGREGATE_ID + " has no store_id");
    }
    storeId = item.get("store_id").s();
    return storeId;
  }

  private static Head toHead(Map<String, AttributeValue> item) {
    var aggregateId = requiredString(item, "aid");
    var typeName = requiredString(item, "type_name");
    var seqNrAttribute = item.get("seq_nr");
    if (seqNrAttribute == null || seqNrAttribute.n() == null) {
      throw new ReadModelUpdateException("head item " + aggregateId + " has no seq_nr number");
    }
    return new Head(aggregateId, typeName, Long.parseLong(seqNrAttribute.n()));
  }

  private static String requiredString(Map<String, AttributeValue> item, String name) {
    var attribute = item.get(name);
    if (attribute == null || attribute.s() == null) {
      throw new ReadModelUpdateException("head item has no " + name + " string");
    }
    return attribute.s();
  }
}
