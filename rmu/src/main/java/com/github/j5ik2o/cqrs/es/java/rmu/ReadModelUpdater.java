package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatEventSerializer;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.MemberId;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.*;
import com.github.j5ik2o.event.store.adapter.java.DeserializationException;
import java.nio.ByteBuffer;
import org.springframework.stereotype.Component;

@Component
public class ReadModelUpdater {
  private final GroupChatMapper groupChatMapper;
  private final MessageMapper messageMapper;
  private final MemberMapper memberMapper;
  GroupChatEventSerializer serializer = new GroupChatEventSerializer();

  public ReadModelUpdater(
      GroupChatMapper groupChatMapper, MessageMapper messageMapper, MemberMapper memberMapper) {
    this.groupChatMapper = groupChatMapper;
    this.messageMapper = messageMapper;
    this.memberMapper = memberMapper;
  }

  public void update(DynamodbEvent dynamodbEvent) {
    dynamodbEvent
        .getRecords()
        .forEach(
            record -> {
              var payloadBytes = getByteBuffer(record);
              try {
                GroupChatEvent groupChatEvent =
                    serializer.deserialize(payloadBytes.array(), GroupChatEvent.class);
                if (groupChatEvent instanceof GroupChatEvent.Created typedEvent) {
                  insertGroupChat(typedEvent);
                } else if (groupChatEvent instanceof GroupChatEvent.Deleted typedEvent) {
                  deleteGroupChat(typedEvent);
                } else if (groupChatEvent instanceof GroupChatEvent.Renamed typedEvent) {
                  renameGroupChat(typedEvent);
                } else if (groupChatEvent instanceof GroupChatEvent.MemberAdded typedEvent) {
                  insertMember(typedEvent);
                } else if (groupChatEvent instanceof GroupChatEvent.MemberRemoved typedEvent) {
                  removeMember(typedEvent);
                } else if (groupChatEvent instanceof GroupChatEvent.MessagePosted typedEvent) {
                  insertMessage(typedEvent);
                } else if (groupChatEvent instanceof GroupChatEvent.MessageDeleted typedEvent) {
                  deleteMessage(typedEvent);
                }
              } catch (DeserializationException e) {
                throw new IllegalStateException("Failed to deserialize event", e);
              }
            });
  }

  private static ByteBuffer getByteBuffer(DynamodbEvent.DynamodbStreamRecord record) {
    var streamRecord = record.getDynamodb();
    if (streamRecord == null) {
      throw new IllegalStateException("streamRecord is null");
    }
    var attributeValues = streamRecord.getNewImage();
    if (attributeValues == null) {
      throw new IllegalStateException("attributeValues is null");
    }
    var payloadAttr = attributeValues.get("payload");
    if (payloadAttr == null || payloadAttr.isNULL()) {
      throw new IllegalStateException("Payload is missing or not a binary type");
    }
    return payloadAttr.getB();
  }

  private void deleteMessage(GroupChatEvent.MessageDeleted typedEvent) {
    messageMapper.deleteMessage(typedEvent.aggregateId().asString());
  }

  private void deleteGroupChat(GroupChatEvent.Deleted typedEvent) {
    groupChatMapper.deleteGroupChat(typedEvent.aggregateId().asString());
  }

  private void removeMember(GroupChatEvent.MemberRemoved typedEvent) {
    memberMapper.deleteMember(
        typedEvent.aggregateId().asString(), typedEvent.member().getUserAccountId().asString());
  }

  private void insertMember(GroupChatEvent.MemberAdded typedEvent) {
    var memberId = MemberId.generate();
    var memberRecord =
        new MemberRecord(
            memberId.asString(),
            typedEvent.aggregateId().asString(),
            typedEvent.executorId().asString(),
            typedEvent.occurredAt(),
            typedEvent.occurredAt());
    memberMapper.insertMember(memberRecord);
  }

  private void insertMessage(GroupChatEvent.MessagePosted typedEvent) {
    var messageRecord =
        new MessageRecord(
            typedEvent.getId(),
            typedEvent.aggregateId().asString(),
            typedEvent.message().getContent(),
            typedEvent.message().getSenderId().asString(),
            typedEvent.occurredAt(),
            typedEvent.occurredAt());
    messageMapper.insertMessage(messageRecord);
  }

  private void renameGroupChat(GroupChatEvent.Renamed typedEvent) {
    groupChatMapper.updateGroupChatName(
        typedEvent.aggregateId().asString(), typedEvent.name().asString());
  }

  private void insertGroupChat(GroupChatEvent.Created typedEvent) {
    var groupChatRecord =
        new GroupChatRecord(
            typedEvent.aggregateId().asString(),
            typedEvent.name().asString(),
            typedEvent.executorId().asString(),
            typedEvent.occurredAt(),
            typedEvent.occurredAt());
    groupChatMapper.insertGroupChat(groupChatRecord);
    var memberId = MemberId.generate();
    var memberRecord =
        new MemberRecord(
            memberId.asString(),
            typedEvent.aggregateId().asString(),
            typedEvent.executorId().asString(),
            typedEvent.occurredAt(),
            typedEvent.occurredAt());
    memberMapper.insertMember(memberRecord);
  }
}
