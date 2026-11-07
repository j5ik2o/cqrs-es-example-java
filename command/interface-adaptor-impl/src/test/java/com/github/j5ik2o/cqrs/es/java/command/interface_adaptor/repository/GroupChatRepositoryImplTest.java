package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatEventSerializer;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatSnapshotSerializer;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatName;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import com.github.j5ik2o.event.store.adapter.java.core.EventStoreConfig;
import com.github.j5ik2o.event.store.adapter.java.core.RetentionPolicy;
import com.github.j5ik2o.event.store.adapter.java.dynamodb.DynamoDbEventStore;
import com.github.j5ik2o.event.store.adapter.java.dynamodb.DynamoDbTableConfig;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

@Testcontainers
class GroupChatRepositoryImplTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(GroupChatRepositoryImplTest.class);

  private static final String JOURNAL_TABLE_NAME = "journal";
  private static final String SNAPSHOT_TABLE_NAME = "snapshot";
  private static final String HEAD_TABLE_NAME = "head";

  private static final String SNAPSHOT_AID_INDEX_NAME = "snapshot-aid-index";

  DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:2.1.0");

  @Container
  public LocalStackContainer localstack =
      new LocalStackContainer(localstackImage).withServices(DYNAMODB);

  @Test
  public void storeAndFindById() throws Exception {
    try (var client = DynamoDBAsyncUtils.createDynamoDbAsyncClient(localstack)) {
      DynamoDBAsyncUtils.createJournalTable(client, JOURNAL_TABLE_NAME).join();
      DynamoDBAsyncUtils.createSnapshotTable(client, SNAPSHOT_TABLE_NAME, SNAPSHOT_AID_INDEX_NAME)
          .join();
      DynamoDBAsyncUtils.createHeadTable(client, HEAD_TABLE_NAME).join();
      client.listTables().join().tableNames().forEach(System.out::println);

      var tables =
          DynamoDbTableConfig.builder()
              .journalTableName(JOURNAL_TABLE_NAME)
              .snapshotTableName(SNAPSHOT_TABLE_NAME)
              .headTableName(HEAD_TABLE_NAME)
              .snapshotAidIndexName(SNAPSHOT_AID_INDEX_NAME)
              .retentionPolicy(RetentionPolicy.none())
              .build();
      var eventStoreConfig =
          EventStoreConfig.<GroupChatEvent, GroupChat>builder()
              .payloadSerializer(new GroupChatEventSerializer())
              .snapshotSerializer(new GroupChatSnapshotSerializer())
              .build();
      var eventStore = DynamoDbEventStore.createAsync(client, tables, eventStoreConfig).join();

      var repository = GroupChatRepositoryImpl.of(eventStore);
      var groupChatId = GroupChatId.generate();
      var groupChatName1 = GroupChatName.of("test-1");
      var adminId = UserAccountId.generate();
      var groupChatAndGroupChatEvent = GroupChat.create(groupChatId, groupChatName1, adminId);
      var groupChatCreated = groupChatAndGroupChatEvent._2;
      var groupChat1 = groupChatAndGroupChatEvent._1;
      repository.store(groupChatCreated, groupChat1).join();

      var groupChatOpt = repository.findById(groupChat1.getId()).join();

      assertTrue(groupChatOpt.isDefined());
      assertEquals(groupChat1, groupChatOpt.get());

      var aid = groupChatId.asString();
      var journalItem =
          client
              .getItem(
                  GetItemRequest.builder()
                      .tableName(JOURNAL_TABLE_NAME)
                      .consistentRead(true)
                      .key(
                          Map.of(
                              "aid", AttributeValue.fromS(aid),
                              "seq_nr", AttributeValue.fromN("1")))
                      .build())
              .join()
              .item();
      assertEquals("GroupChatCreated@1", journalItem.get("manifest").s());
      var payload = new ObjectMapper().readTree(journalItem.get("payload").b().asByteArray());
      assertEquals("GroupChatCreated", payload.get("type").asText());
      assertFalse(payload.has("aggregateId"));
      assertFalse(payload.has("sequenceNumber"));
      assertFalse(payload.has("occurredAt"));

      var headItem =
          client
              .getItem(
                  GetItemRequest.builder()
                      .tableName(HEAD_TABLE_NAME)
                      .consistentRead(true)
                      .key(Map.of("aid", AttributeValue.fromS(aid)))
                      .build())
              .join()
              .item();
      assertEquals("1", headItem.get("seq_nr").n());

      var snapshotItem =
          client
              .getItem(
                  GetItemRequest.builder()
                      .tableName(SNAPSHOT_TABLE_NAME)
                      .consistentRead(true)
                      .key(
                          Map.of(
                              "aid", AttributeValue.fromS(aid),
                              "skey", AttributeValue.fromN("0")))
                      .build())
              .join()
              .item();
      assertEquals("GroupChat@1", snapshotItem.get("manifest").s());
      assertEquals("1", snapshotItem.get("seq_nr").n());

      var renamed = groupChat1.rename(GroupChatName.of("test-2"), adminId).get();
      repository.store(renamed._2, renamed._1).join();

      var renamedOpt = repository.findById(groupChat1.getId()).join();

      assertTrue(renamedOpt.isDefined());
      assertEquals("test-2", renamedOpt.get().getName().asString());
      assertEquals(2L, renamedOpt.get().getSequenceNumber());

      var stale = groupChat1.rename(GroupChatName.of("stale"), adminId).get();
      assertThrows(CompletionException.class, () -> repository.store(stale._2, stale._1).join());
    }
  }
}
