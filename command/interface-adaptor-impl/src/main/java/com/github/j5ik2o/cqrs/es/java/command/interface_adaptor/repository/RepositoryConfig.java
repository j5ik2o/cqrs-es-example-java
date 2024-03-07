package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository;

import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatEventSerializer;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatSnapshotSerializer;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.event.store.adapter.java.EventStoreAsync;
import io.vavr.control.Option;
import java.util.function.BiPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

@Configuration
@Profile("write")
public class RepositoryConfig {
  private static final String JOURNAL_TABLE_NAME = "journal";
  private static final String SNAPSHOT_TABLE_NAME = "snapshot";

  private static final String JOURNAL_AID_INDEX_NAME = "journal-aid-index";
  private static final String SNAPSHOT_AID_INDEX_NAME = "snapshot-aid-index";

  @Bean
  public DynamoDbAsyncClient dynamoDbAsyncClient() {
    return DynamoDbAsyncClient.create();
  }

  @Bean
  public EventStoreAsync<GroupChatId, GroupChat, GroupChatEvent> eventStore(
      DynamoDbAsyncClient dynamoDbAsyncClient) {
    return EventStoreAsync.<GroupChatId, GroupChat, GroupChatEvent>ofDynamoDB(
            dynamoDbAsyncClient,
            JOURNAL_TABLE_NAME,
            SNAPSHOT_TABLE_NAME,
            JOURNAL_AID_INDEX_NAME,
            SNAPSHOT_AID_INDEX_NAME,
            32)
        .withSnapshotSerializer(new GroupChatSnapshotSerializer())
        .withEventSerializer(new GroupChatEventSerializer());
  }

  @Bean
  public Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt() {
    return GroupChatRepositoryImpl.retentionCriteriaOf(100L);
  }

  @Bean
  public GroupChatRepositoryImpl groupChatRepository(
      EventStoreAsync<GroupChatId, GroupChat, GroupChatEvent> eventStore,
      Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt) {
    return GroupChatRepositoryImpl.of(eventStore, snapshotPredicateOpt);
  }
}
