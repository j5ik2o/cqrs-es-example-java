package com.github.j5ik2o.cqrs.es.java.interface_adaptor.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatName;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import com.github.j5ik2o.cqrs.es.java.interface_adaptor.repository.serialization.GroupChatEventSerializer;
import com.github.j5ik2o.cqrs.es.java.interface_adaptor.repository.serialization.GroupChatSnapshotSerializer;
import com.github.j5ik2o.event.store.adapter.java.EventStoreAsync;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class GroupChatRepositoryImplTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(GroupChatRepositoryImplTest.class);

  private static final String JOURNAL_TABLE_NAME = "journal";
  private static final String SNAPSHOT_TABLE_NAME = "snapshot";

  private static final String JOURNAL_AID_INDEX_NAME = "journal-aid-index";
  private static final String SNAPSHOT_AID_INDEX_NAME = "snapshot-aid-index";

  DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:2.1.0");

  @Container
  public LocalStackContainer localstack =
      new LocalStackContainer(localstackImage).withServices(DYNAMODB);

  @Test
  public void storeAndFindById() {
    try (var client = DynamoDBAsyncUtils.createDynamoDbAsyncClient(localstack)) {
      DynamoDBAsyncUtils.createJournalTable(client, JOURNAL_TABLE_NAME, JOURNAL_AID_INDEX_NAME)
          .join();
      DynamoDBAsyncUtils.createSnapshotTable(client, SNAPSHOT_TABLE_NAME, SNAPSHOT_AID_INDEX_NAME)
          .join();
      client.listTables().join().tableNames().forEach(System.out::println);

      var eventStore =
          EventStoreAsync.<GroupChatId, GroupChat, GroupChatEvent>ofDynamoDB(
                  client,
                  JOURNAL_TABLE_NAME,
                  SNAPSHOT_TABLE_NAME,
                  JOURNAL_AID_INDEX_NAME,
                  SNAPSHOT_AID_INDEX_NAME,
                  32)
              .withSnapshotSerializer(new GroupChatSnapshotSerializer())
              .withEventSerializer(new GroupChatEventSerializer());

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
    }
  }
}
