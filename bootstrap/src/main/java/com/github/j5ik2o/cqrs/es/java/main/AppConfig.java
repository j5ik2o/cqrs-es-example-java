package com.github.j5ik2o.cqrs.es.java.main;

import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatEventSerializer;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatSnapshotSerializer;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.event.store.adapter.java.EventStoreAsync;
import java.net.URI;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
@ToString
public class AppConfig {

  private DynamoDb dynamoDb;
  private Stream stream;
  private Persistence persistence;

  public boolean hasDynamoDbConfig() {
    return dynamoDb != null && dynamoDb.hasConfig();
  }

  @Getter
  @Setter
  @ToString
  public static class DynamoDb {
    private String region;

    private String endpointUrl;

    private String accessKey;

    private String secretAccessKey;

    public boolean hasConfig() {
      return region != null && endpointUrl != null && accessKey != null && secretAccessKey != null;
    }
  }

  @Getter
  @Setter
  @ToString
  public static class Stream {
    private String journalTableName;
    private int maxItemCount;
  }

  @Getter
  @Setter
  @ToString
  public static class Persistence {
    private String journalTableName;
    private String snapshotTableName;
    private String journalAidIndexName;
    private String snapshotAidIndexName;
    private int shardCount;
    private long snapshotInterval;
  }

  @Bean
  public DynamoDbClient dynamoDbClient() {
    if (hasDynamoDbConfig()) {
      return DynamoDbClient.builder()
          .endpointOverride(URI.create(dynamoDb.endpointUrl))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(dynamoDb.accessKey, dynamoDb.secretAccessKey)))
          .build();
    } else {
      return DynamoDbClient.create();
    }
  }

  @Bean
  public DynamoDbAsyncClient dynamoDbAsyncClient() {
    if (hasDynamoDbConfig()) {
      return DynamoDbAsyncClient.builder()
          .endpointOverride(URI.create(dynamoDb.endpointUrl))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(dynamoDb.accessKey, dynamoDb.secretAccessKey)))
          .build();
    } else {
      return DynamoDbAsyncClient.create();
    }
  }

  @Bean
  public DynamoDbStreamsClient dynamoDbStreamsClient() {
    if (hasDynamoDbConfig()) {
      return DynamoDbStreamsClient.builder()
          .endpointOverride(URI.create(dynamoDb.endpointUrl))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(dynamoDb.accessKey, dynamoDb.secretAccessKey)))
          .build();
    } else {
      return DynamoDbStreamsClient.create();
    }
  }

  @Bean
  public DynamoDbStreamsAsyncClient dynamoDbStreamsAsyncClient() {
    if (hasDynamoDbConfig()) {
      return DynamoDbStreamsAsyncClient.builder()
          .endpointOverride(URI.create(dynamoDb.endpointUrl))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(dynamoDb.accessKey, dynamoDb.secretAccessKey)))
          .build();
    } else {
      return DynamoDbStreamsAsyncClient.create();
    }
  }

  @Bean
  public EventStoreAsync<GroupChatId, GroupChat, GroupChatEvent> eventStoreAsync(
      DynamoDbAsyncClient dynamoDbAsyncClient) {
    return EventStoreAsync.<GroupChatId, GroupChat, GroupChatEvent>ofDynamoDB(
            dynamoDbAsyncClient,
            persistence.journalTableName,
            persistence.snapshotTableName,
            persistence.journalAidIndexName,
            persistence.snapshotAidIndexName,
            persistence.shardCount)
        .withSnapshotSerializer(new GroupChatSnapshotSerializer())
        .withEventSerializer(new GroupChatEventSerializer());
  }
}
