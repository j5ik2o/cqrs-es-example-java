package com.github.j5ik2o.cqrs.es.java.main;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.github.j5ik2o.cqrs.es.java.rmu.ReadModelUpdater;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import java.util.ArrayList;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.Record;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

@Component
@Profile("local-rmu")
public class CommandLineRunnerImpl implements CommandLineRunner {
  static final Logger LOGGER = LoggerFactory.getLogger(CommandLineRunnerImpl.class);
  private final ReadModelUpdater readModelUpdater;
  private final AppConfig appConfig;

  public CommandLineRunnerImpl(ReadModelUpdater readModelUpdater, AppConfig appConfig) {
    this.readModelUpdater = readModelUpdater;
    this.appConfig = appConfig;
  }

  @Override
  public void run(String... args) throws Exception {
    LOGGER.info("appConfig = {}", appConfig);
    // var dynamodbClient = DynamoDbClient.create();
    // var dynamodbStreamsClient = DynamoDbStreamsClient.create();
    // streamDriver(dynamodbClient, dynamodbStreamsClient, appConfig.getJournalTableName(), 100);
  }

  private Map<String, AttributeValue> getItem(Record record) {
    var streamRecord = record.dynamodb();
    var item = streamRecord.newImage();
    return convertAttributeRecordToMap(HashMap.ofAll(item));
  }

  private Map<String, AttributeValue> getKeys(Record record) {
    var streamRecord = record.dynamodb();
    var keys = streamRecord.keys();
    return convertAttributeRecordToMap(HashMap.ofAll(keys));
  }

  private Map<String, AttributeValue> convertAttributeRecordToMap(
      Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>
          sdkAttributeValueMap) {
    return sdkAttributeValueMap.map((k, v) -> Map.entry(k, convertAttributeValue(v)));
  }

  private AttributeValue convertAttributeValue(
      software.amazon.awssdk.services.dynamodb.model.AttributeValue v) {
    if (v.s() != null) {
      return new AttributeValue(v.s());
    } else if (v.n() != null) {
      return new AttributeValue(v.n());
    } else if (v.b() != null) {
      var av = new AttributeValue();
      av.setB(v.b().asByteBuffer());
      return av;
    } else if (v.bool() != null) {
      var av = new AttributeValue();
      av.setBOOL(v.bool());
      return av;
    } else if (v.nul()) {
      var av = new AttributeValue();
      av.setNULL(true);
      return av;
    } else if (v.hasBs() && v.bs() != null) {
      var av = new AttributeValue();
      av.setBS(v.bs().stream().map(BytesWrapper::asByteBuffer).toList());
      return av;
    } else if (v.hasSs() && v.ss() != null) {
      return new AttributeValue(v.ss());
    } else if (v.hasNs() && v.ns() != null) {
      return new AttributeValue(v.ns());
    } else if (v.hasL() && v.l() != null) {
      var av = new AttributeValue();
      av.setL(v.l().stream().map(this::convertAttributeValue).toList());
      return av;
    } else if (v.hasM() && v.m() != null) {
      var av = new AttributeValue();
      var m =
          HashMap.ofAll(v.m()).map((k, v1) -> Map.entry(k, convertAttributeValue(v1))).toJavaMap();
      av.setM(m);
      return av;
    } else {
      throw new IllegalArgumentException("unknown type");
    }
  }

  private void streamDriver(
      DynamoDbClient dynamodbClient,
      DynamoDbStreamsClient dynamodbStreamsClient,
      String journalTableName,
      int maxItemCount) {
    var describeTableResponse =
        dynamodbClient.describeTable(
            DescribeTableRequest.builder().tableName(journalTableName).build());
    var streamArn = describeTableResponse.table().latestStreamArn();

    String lastEvaluatedShardId = null;

    for (; ; ) {
      var builder = DescribeStreamRequest.builder().streamArn(streamArn);
      if (lastEvaluatedShardId != null) {
        builder = builder.exclusiveStartShardId(lastEvaluatedShardId);
      }
      var describeStreamResponse = dynamodbStreamsClient.describeStream(builder.build());
      var shards = describeStreamResponse.streamDescription().shards();

      for (var shard : shards) {
        var shardId = shard.shardId();
        var getShardIteratorResponse =
            dynamodbStreamsClient.getShardIterator(
                GetShardIteratorRequest.builder()
                    .shardId(shardId)
                    .shardIteratorType(ShardIteratorType.LATEST)
                    .streamArn(streamArn)
                    .build());
        var shardIterator = getShardIteratorResponse.shardIterator();
        int count = 0;
        while (shardIterator != null && count < maxItemCount) {
          var getRecordsResponse =
              dynamodbStreamsClient.getRecords(
                  GetRecordsRequest.builder().shardIterator(shardIterator).limit(100).build());
          var records = getRecordsResponse.records();
          for (var record : records) {
            var keys = getKeys(record);
            var item = getItem(record);
            LOGGER.info("keys = {}, item = {}", keys, item);
            var event = convertToEvent(record, keys, item, streamArn);
            readModelUpdater.update(event);
          }
          count += records.size();
          shardIterator = getRecordsResponse.nextShardIterator();
        }
      }
      if (describeStreamResponse.streamDescription().lastEvaluatedShardId() == null) {
        break;
      }
      lastEvaluatedShardId = describeStreamResponse.streamDescription().lastEvaluatedShardId();
    }
  }

  private DynamodbEvent convertToEvent(
      Record record,
      Map<String, AttributeValue> keys,
      Map<String, AttributeValue> item,
      String streamArn) {
    var event = new DynamodbEvent();
    var streamRecord = new DynamodbEvent.DynamodbStreamRecord();
    streamRecord.setAwsRegion(record.awsRegion());
    streamRecord.setEventID(record.eventID());
    streamRecord.setEventName(record.eventName().name());
    streamRecord.setEventSource(record.eventSource());
    streamRecord.setEventSourceARN(streamArn);
    streamRecord.setEventVersion(record.eventVersion());
    var sr = new com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord();
    sr.setKeys(keys.toJavaMap());
    sr.setNewImage(item.toJavaMap());
    sr.setApproximateCreationDateTime(Date.from(record.dynamodb().approximateCreationDateTime()));
    sr.setSequenceNumber(record.dynamodb().sequenceNumber());
    sr.setSizeBytes(record.dynamodb().sizeBytes());
    sr.setStreamViewType(
        com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamViewType.valueOf(
            record.dynamodb().streamViewType().name()));
    streamRecord.setDynamodb(sr);
    var streamRecords = new ArrayList<DynamodbEvent.DynamodbStreamRecord>();
    streamRecords.add(streamRecord);
    event.setRecords(streamRecords);
    return event;
  }
}
