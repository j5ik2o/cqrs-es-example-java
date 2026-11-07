package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import java.util.ArrayList;
import java.util.Date;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeStreamRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetRecordsRequest;
import software.amazon.awssdk.services.dynamodb.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.dynamodb.model.Record;
import software.amazon.awssdk.services.dynamodb.model.ShardIteratorType;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

/** DynamoDB Streams implementation of {@link HeadStreamClient}. */
public final class DynamoDBHeadStreamClient implements HeadStreamClient {
  private final DynamoDbClient dynamoDbClient;
  private final DynamoDbStreamsClient streamsClient;

  public DynamoDBHeadStreamClient(
      DynamoDbClient dynamoDbClient, DynamoDbStreamsClient streamsClient) {
    this.dynamoDbClient = dynamoDbClient;
    this.streamsClient = streamsClient;
  }

  @Override
  public String streamArn(String headTableName) {
    var response =
        dynamoDbClient.describeTable(
            DescribeTableRequest.builder().tableName(headTableName).build());
    var streamArn = response.table().latestStreamArn();
    if (streamArn == null) {
      throw new IllegalStateException("head table " + headTableName + " has no stream");
    }
    return streamArn;
  }

  @Override
  public ShardPage describeShards(String streamArn, String exclusiveStartShardId) {
    var builder = DescribeStreamRequest.builder().streamArn(streamArn);
    if (exclusiveStartShardId != null) {
      builder = builder.exclusiveStartShardId(exclusiveStartShardId);
    }
    var description = streamsClient.describeStream(builder.build()).streamDescription();
    var shardIds = description.shards().stream().map(shard -> shard.shardId()).toList();
    return new ShardPage(shardIds, description.lastEvaluatedShardId());
  }

  @Override
  public StreamRecordPage getRecords(String streamArn, String shardIterator, int limit) {
    var response =
        streamsClient.getRecords(
            GetRecordsRequest.builder().shardIterator(shardIterator).limit(limit).build());
    var records = new ArrayList<DynamodbEvent.DynamodbStreamRecord>();
    var sequenceNumbers = new ArrayList<String>();
    for (var record : response.records()) {
      records.add(toEvent(record, streamArn));
      sequenceNumbers.add(record.dynamodb().sequenceNumber());
    }
    return new StreamRecordPage(records, sequenceNumbers, response.nextShardIterator());
  }

  @Override
  public String shardIteratorAtTrimHorizon(String streamArn, String shardId) {
    return getShardIterator(streamArn, shardId, ShardIteratorType.TRIM_HORIZON, null);
  }

  @Override
  public String shardIteratorAfterSequenceNumber(
      String streamArn, String shardId, String sequenceNumber) {
    return getShardIterator(
        streamArn, shardId, ShardIteratorType.AFTER_SEQUENCE_NUMBER, sequenceNumber);
  }

  private String getShardIterator(
      String streamArn, String shardId, ShardIteratorType type, String sequenceNumber) {
    var builder =
        GetShardIteratorRequest.builder()
            .streamArn(streamArn)
            .shardId(shardId)
            .shardIteratorType(type);
    if (sequenceNumber != null) {
      builder = builder.sequenceNumber(sequenceNumber);
    }
    return streamsClient.getShardIterator(builder.build()).shardIterator();
  }

  private DynamodbEvent.DynamodbStreamRecord toEvent(Record record, String streamArn) {
    var streamRecord = new DynamodbEvent.DynamodbStreamRecord();
    streamRecord.setAwsRegion(record.awsRegion());
    streamRecord.setEventID(record.eventID());
    streamRecord.setEventName(record.eventNameAsString());
    streamRecord.setEventSource(record.eventSource());
    streamRecord.setEventSourceARN(streamArn);
    streamRecord.setEventVersion(record.eventVersion());
    var sr = new com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord();
    sr.setKeys(convertAttributeMap(record.dynamodb().keys()));
    sr.setNewImage(convertAttributeMap(record.dynamodb().newImage()));
    var approximateCreationDateTime = record.dynamodb().approximateCreationDateTime();
    if (approximateCreationDateTime != null) {
      sr.setApproximateCreationDateTime(Date.from(approximateCreationDateTime));
    }
    sr.setSequenceNumber(record.dynamodb().sequenceNumber());
    sr.setSizeBytes(record.dynamodb().sizeBytes());
    var streamViewType = record.dynamodb().streamViewType();
    if (streamViewType != null) {
      sr.setStreamViewType(
          com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamViewType.valueOf(
              streamViewType.name()));
    }
    streamRecord.setDynamodb(sr);
    return streamRecord;
  }

  private java.util.Map<String, AttributeValue> convertAttributeMap(
      java.util.Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>
          attributeValues) {
    if (attributeValues == null) {
      return null;
    }
    return HashMap.ofAll(attributeValues)
        .map((key, value) -> Map.entry(key, convertAttributeValue(value)))
        .toJavaMap();
  }

  private AttributeValue convertAttributeValue(
      software.amazon.awssdk.services.dynamodb.model.AttributeValue value) {
    if (value.s() != null) {
      return new AttributeValue(value.s());
    } else if (value.n() != null) {
      var attribute = new AttributeValue();
      attribute.setN(value.n());
      return attribute;
    } else if (value.b() != null) {
      var attribute = new AttributeValue();
      attribute.setB(value.b().asByteBuffer());
      return attribute;
    } else if (value.bool() != null) {
      var attribute = new AttributeValue();
      attribute.setBOOL(value.bool());
      return attribute;
    } else if (value.nul() != null && value.nul()) {
      var attribute = new AttributeValue();
      attribute.setNULL(true);
      return attribute;
    } else if (value.hasBs() && value.bs() != null) {
      var attribute = new AttributeValue();
      attribute.setBS(value.bs().stream().map(BytesWrapper::asByteBuffer).toList());
      return attribute;
    } else if (value.hasSs() && value.ss() != null) {
      return new AttributeValue(value.ss());
    } else if (value.hasNs() && value.ns() != null) {
      return new AttributeValue(value.ns());
    } else if (value.hasL() && value.l() != null) {
      var attribute = new AttributeValue();
      attribute.setL(value.l().stream().map(this::convertAttributeValue).toList());
      return attribute;
    } else if (value.hasM() && value.m() != null) {
      var attribute = new AttributeValue();
      attribute.setM(convertAttributeMap(value.m()));
      return attribute;
    } else {
      throw new IllegalArgumentException("unknown DynamoDB attribute type");
    }
  }
}
