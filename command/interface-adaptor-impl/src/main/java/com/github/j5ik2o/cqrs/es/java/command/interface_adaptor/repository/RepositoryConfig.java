package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository;

import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatEventSerializer;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatSnapshotSerializer;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.event.store.adapter.java.core.AsyncEventStore;
import com.github.j5ik2o.event.store.adapter.java.core.EventStoreConfig;
import com.github.j5ik2o.event.store.adapter.java.core.RetentionPolicy;
import com.github.j5ik2o.event.store.adapter.java.dynamodb.DynamoDbEventStore;
import com.github.j5ik2o.event.store.adapter.java.dynamodb.DynamoDbTableConfig;
import io.vavr.control.Option;
import java.net.URI;
import java.util.function.BiPredicate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

@Configuration
@Profile("write")
public class RepositoryConfig {

  @Value("${app.persistence.journal-table-name}")
  private String journalTableName;

  @Value("${app.persistence.snapshot-table-name}")
  private String snapshotTableName;

  @Value("${app.persistence.head-table-name}")
  private String headTableName;

  @Value("${app.persistence.snapshot-aid-index-name}")
  private String snapshotAidIndexName;

  @Value("${app.persistence.snapshot-interval}")
  private long snapshotInterval;

  @Value("${app.dynamo-db.endpoint-url}")
  private String dynamodbEndpointUrl;

  @Value("${app.dynamo-db.access-key}")
  private String dynamodbAccessKey;

  @Value("${app.dynamo-db.secret-access-key}")
  private String dynamodbSecretAccessKey;

  public boolean hasDynamoDbConfig() {
    return dynamodbEndpointUrl != null
        && dynamodbAccessKey != null
        && dynamodbSecretAccessKey != null;
  }

  @Bean(name = "dynamoDbAsyncClient2")
  public DynamoDbAsyncClient dynamoDbAsyncClient() {
    if (hasDynamoDbConfig()) {
      return DynamoDbAsyncClient.builder()
          .endpointOverride(URI.create(dynamodbEndpointUrl))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(dynamodbAccessKey, dynamodbSecretAccessKey)))
          .build();
    } else {
      return DynamoDbAsyncClient.create();
    }
  }

  @Bean
  public Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt() {
    return GroupChatRepositoryImpl.retentionCriteriaOf(snapshotInterval);
  }

  @Bean
  public AsyncEventStore<GroupChatEvent, GroupChat> eventStoreAsync(
      @Qualifier("dynamoDbAsyncClient2") DynamoDbAsyncClient dynamoDbAsyncClient2) {
    var tables =
        DynamoDbTableConfig.builder()
            .journalTableName(journalTableName)
            .snapshotTableName(snapshotTableName)
            .headTableName(headTableName)
            .snapshotAidIndexName(snapshotAidIndexName)
            .retentionPolicy(RetentionPolicy.none())
            .build();
    var eventStoreConfig =
        EventStoreConfig.<GroupChatEvent, GroupChat>builder()
            .payloadSerializer(new GroupChatEventSerializer())
            .snapshotSerializer(new GroupChatSnapshotSerializer())
            .build();
    return DynamoDbEventStore.createAsync(dynamoDbAsyncClient2, tables, eventStoreConfig).join();
  }

  @Bean
  public GroupChatRepositoryImpl groupChatRepository(
      AsyncEventStore<GroupChatEvent, GroupChat> eventStoreAsync,
      Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt) {
    return GroupChatRepositoryImpl.of(eventStoreAsync, snapshotPredicateOpt);
  }
}
