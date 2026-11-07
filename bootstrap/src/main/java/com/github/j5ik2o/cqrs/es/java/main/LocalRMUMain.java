package com.github.j5ik2o.cqrs.es.java.main;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.github.j5ik2o.cqrs.es.java.rmu.ReadModelUpdater;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
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
public class LocalRMUMain implements CommandLineRunner {
  static final Logger LOGGER = LoggerFactory.getLogger(LocalRMUMain.class);
  private static final long POLL_INTERVAL_MILLIS = 1000L;

  private final ReadModelUpdater readModelUpdater;
  private final AppConfig appConfig;
  private final DynamoDbConfig dynamoDbConfig;

  public LocalRMUMain(
      ReadModelUpdater readModelUpdater, AppConfig appConfig, DynamoDbConfig dynamoDbConfig) {
    this.readModelUpdater = readModelUpdater;
    this.appConfig = appConfig;
    this.dynamoDbConfig = dynamoDbConfig;
  }

  @Override
  public void run(String... args) throws Exception {
    LOGGER.info("appConfig = {}", appConfig);
    var dynamodbClient = dynamoDbConfig.dynamoDbClient();
    var dynamodbStreamsClient = dynamoDbConfig.dynamoDbStreamsClient();

    while (true) {
      try {
        streamDriver(
            dynamodbClient,
            dynamodbStreamsClient,
            appConfig.getStream().getHeadTableName(),
            appConfig.getStream().getMaxItemCount());
      } catch (Exception ex) {
        LOGGER.warn(
            "An error has occurred, but stream processing is restarted. If this error persists, the read model condition may be incorrect.",
            ex);
      }
      Thread.sleep(POLL_INTERVAL_MILLIS);
    }
  }

  /**
   * Polls every shard of the head table's stream. The first iterator starts at TRIM_HORIZON so a
   * restart replays the retained records; the read model positions drop what was already applied.
   */
  private void streamDriver(
      DynamoDbClient dynamodbClient,
      DynamoDbStreamsClient dynamodbStreamsClient,
      String headTableName,
      int maxItemCount)
      throws InterruptedException {
    var describeTableResponse =
        dynamodbClient.describeTable(
            DescribeTableRequest.builder().tableName(headTableName).build());
    var streamArn = describeTableResponse.table().latestStreamArn();
    if (streamArn == null) {
      throw new IllegalStateException("head table " + headTableName + " has no stream");
    }

    var iterators = new java.util.HashMap<String, String>();
    Set<String> completedShards = new HashSet<>();

    while (true) {
      String lastEvaluatedShardId = null;
      do {
        var builder = DescribeStreamRequest.builder().streamArn(streamArn);
        if (lastEvaluatedShardId != null) {
          builder = builder.exclusiveStartShardId(lastEvaluatedShardId);
        }
        var describeStreamResponse = dynamodbStreamsClient.describeStream(builder.build());
        for (var shard : describeStreamResponse.streamDescription().shards()) {
          var shardId = shard.shardId();
          if (completedShards.contains(shardId)) {
            continue;
          }
          var shardIterator = iterators.get(shardId);
          if (shardIterator == null) {
            shardIterator =
                dynamodbStreamsClient
                    .getShardIterator(
                        GetShardIteratorRequest.builder()
                            .shardId(shardId)
                            .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
                            .streamArn(streamArn)
                            .build())
                    .shardIterator();
          }
          while (shardIterator != null) {
            var getRecordsResponse =
                dynamodbStreamsClient.getRecords(
                    GetRecordsRequest.builder()
                        .shardIterator(shardIterator)
                        .limit(maxItemCount)
                        .build());
            var records = getRecordsResponse.records();
            if (!records.isEmpty()) {
              LOGGER.info("records = {}", records.stream().map(Record::eventID).toList());
            }
            for (var record : records) {
              readModelUpdater.update(convertToEvent(record, streamArn));
            }
            var nextShardIterator = getRecordsResponse.nextShardIterator();
            if (nextShardIterator == null) {
              iterators.remove(shardId);
              completedShards.add(shardId);
              break;
            }
            iterators.put(shardId, nextShardIterator);
            shardIterator = nextShardIterator;
            if (records.isEmpty()) {
              break;
            }
          }
        }
        lastEvaluatedShardId = describeStreamResponse.streamDescription().lastEvaluatedShardId();
      } while (lastEvaluatedShardId != null);
      Thread.sleep(POLL_INTERVAL_MILLIS);
    }
  }

  private java.util.Map<String, AttributeValue> getItem(Record record) {
    var streamRecord = record.dynamodb();
    var item = streamRecord.newImage();
    if (item == null) {
      return null;
    }
    return convertAttributeRecordToMap(HashMap.ofAll(item)).toJavaMap();
  }

  private java.util.Map<String, AttributeValue> getKeys(Record record) {
    var streamRecord = record.dynamodb();
    var keys = streamRecord.keys();
    return convertAttributeRecordToMap(HashMap.ofAll(keys)).toJavaMap();
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
      var av = new AttributeValue();
      av.setN(v.n());
      return av;
    } else if (v.b() != null) {
      var av = new AttributeValue();
      av.setB(v.b().asByteBuffer());
      return av;
    } else if (v.bool() != null) {
      var av = new AttributeValue();
      av.setBOOL(v.bool());
      return av;
    } else if (v.nul() != null && v.nul()) {
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

  private DynamodbEvent convertToEvent(Record record, String streamArn) {
    var event = new DynamodbEvent();
    var streamRecord = new DynamodbEvent.DynamodbStreamRecord();
    streamRecord.setAwsRegion(record.awsRegion());
    streamRecord.setEventID(record.eventID());
    streamRecord.setEventName(record.eventNameAsString());
    streamRecord.setEventSource(record.eventSource());
    streamRecord.setEventSourceARN(streamArn);
    streamRecord.setEventVersion(record.eventVersion());
    var sr = new com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord();
    sr.setKeys(getKeys(record));
    sr.setNewImage(getItem(record));
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
    var streamRecords = new ArrayList<DynamodbEvent.DynamodbStreamRecord>();
    streamRecords.add(streamRecord);
    event.setRecords(streamRecords);
    return event;
  }
}
