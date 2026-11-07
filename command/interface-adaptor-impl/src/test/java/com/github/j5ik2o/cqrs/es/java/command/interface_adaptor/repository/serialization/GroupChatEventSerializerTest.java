package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatName;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Member;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.MemberId;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.MemberRole;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Message;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.MessageId;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupChatEventSerializerTest {

  private final GroupChatEventSerializer serializer = new GroupChatEventSerializer();
  private final GroupChatSnapshotSerializer snapshotSerializer = new GroupChatSnapshotSerializer();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void payloadDoesNotContainEnvelopeMetadata() throws Exception {
    var id = GroupChatId.generate();
    var name = GroupChatName.of("test");
    var executorId = UserAccountId.generate();
    var created = GroupChat.create(id, name, executorId)._2;

    var bytes = serializer.serialize(created);
    var json = objectMapper.readTree(bytes);

    assertEquals(GroupChatEvent.CREATED_TYPE, json.get("type").asText());
    assertEquals("GroupChatCreated@1", created.manifest());
    assertTrue(json.has("id"));
    assertTrue(json.has("name"));
    assertTrue(json.has("members"));
    assertTrue(json.has("executorId"));
    assertFalse(json.has("aggregateId"));
    assertFalse(json.has("sequenceNumber"));
    assertFalse(json.has("occurredAt"));
    assertFalse(json.has("created"));
    assertFalse(json.has("typeName"));
    assertFalse(json.has("manifest"));
  }

  @Test
  void deserializesPayloadWithoutEnvelopeMetadata() {
    var id = GroupChatId.generate();
    var name = GroupChatName.of("test");
    var executorId = UserAccountId.generate();
    var created = GroupChat.create(id, name, executorId)._2;

    var restored = serializer.deserialize(serializer.serialize(created));

    assertInstanceOf(GroupChatEvent.Created.class, restored);
    var restoredCreated = (GroupChatEvent.Created) restored;
    assertEquals(name, restoredCreated.name());
    assertEquals(executorId, restoredCreated.executorId());
    assertEquals(created.getId(), restoredCreated.getId());
  }

  @Test
  void everyEventTypeHasManifestAndTypeDiscriminator() throws Exception {
    var id = GroupChatId.generate();
    var name = GroupChatName.of("test");
    var executorId = UserAccountId.generate();
    var created = GroupChat.create(id, name, executorId)._2;
    var groupChat = GroupChat.create(id, name, executorId)._1;
    var events =
        List.of(
            created,
            groupChat.rename(GroupChatName.of("renamed"), executorId).get()._2,
            groupChat
                .addMember(
                    Member.of(MemberId.generate(), UserAccountId.generate(), MemberRole.MEMBER),
                    executorId)
                .get()
                ._2);

    for (var event : events) {
      var json = objectMapper.readTree(serializer.serialize(event));
      assertEquals(event.typeName(), json.get("type").asText());
      assertTrue(event.manifest().startsWith(event.typeName() + "@"));
      assertFalse(json.has("aggregateId"));
      assertFalse(json.has("sequenceNumber"));
      assertFalse(json.has("occurredAt"));
    }
  }

  @Test
  void messageEditedPayloadKeepsItsType() {
    var id = GroupChatId.generate();
    var executorId = UserAccountId.generate();
    var groupChat = GroupChat.create(id, GroupChatName.of("test"), executorId)._1;
    var message = Message.of(MessageId.generate(), "before", executorId, LocalDateTime.now());
    var posted = groupChat.postMessage(message, executorId).get()._1;
    var edited = posted.editMessage(message.getId(), "after", executorId).get()._2;

    var restored = serializer.deserialize(serializer.serialize(edited));

    assertEquals("GroupChatMessageEdited", edited.typeName());
    assertEquals("GroupChatMessageEdited@1", edited.manifest());
    assertInstanceOf(GroupChatEvent.MessageEdited.class, restored);
    assertEquals("after", ((GroupChatEvent.MessageEdited) restored).message().getContent());
  }

  @Test
  void snapshotPayloadDoesNotContainVersion() throws Exception {
    var groupChat =
        GroupChat.create(GroupChatId.generate(), GroupChatName.of("test"), UserAccountId.generate())
            ._1;

    var json = objectMapper.readTree(snapshotSerializer.serialize(groupChat));

    assertTrue(json.has("sequenceNumber"));
    assertFalse(json.has("version"));
    assertEquals(
        groupChat, snapshotSerializer.deserialize(snapshotSerializer.serialize(groupChat)));
  }
}
