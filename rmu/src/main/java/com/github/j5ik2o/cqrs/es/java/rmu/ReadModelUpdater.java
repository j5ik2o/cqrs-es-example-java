package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatEventSerializer;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.MemberId;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.MemberRole;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.*;
import com.github.j5ik2o.event.store.adapter.java.core.SerializationException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReadModelUpdater {
  private final GroupChatWriteMapper groupChatWriteMapper;
  private final MessageWriteMapper messageWriteMapper;
  private final MemberWriteMapper memberWriteMapper;
  GroupChatEventSerializer serializer = new GroupChatEventSerializer();

  public ReadModelUpdater(
      GroupChatWriteMapper groupChatWriteMapper,
      MessageWriteMapper messageWriteMapper,
      MemberWriteMapper memberWriteMapper) {
    this.groupChatWriteMapper = groupChatWriteMapper;
    this.messageWriteMapper = messageWriteMapper;
    this.memberWriteMapper = memberWriteMapper;
  }

  public void update(DynamodbEvent dynamodbEvent) {
    dynamodbEvent
        .getRecords()
        .forEach(
            record -> {
              var payloadBytes = getByteBuffer(record);
              var aggregateId = getAggregateId(record);
              var occurredAt = getOccurredAt(record);
              try {
                byte[] bytes = new byte[payloadBytes.remaining()];
                payloadBytes.get(bytes);

                GroupChatEvent groupChatEvent = serializer.deserialize(bytes);
                if (groupChatEvent instanceof GroupChatEvent.Created typedEvent) {
                  insertGroupChat(typedEvent, aggregateId, occurredAt);
                } else if (groupChatEvent instanceof GroupChatEvent.Deleted typedEvent) {
                  deleteGroupChat(aggregateId);
                } else if (groupChatEvent instanceof GroupChatEvent.Renamed typedEvent) {
                  renameGroupChat(typedEvent, aggregateId, occurredAt);
                } else if (groupChatEvent instanceof GroupChatEvent.MemberAdded typedEvent) {
                  insertMember(typedEvent, aggregateId, occurredAt);
                } else if (groupChatEvent instanceof GroupChatEvent.MemberRemoved typedEvent) {
                  removeMember(typedEvent, aggregateId);
                } else if (groupChatEvent instanceof GroupChatEvent.MessagePosted typedEvent) {
                  insertMessage(typedEvent, aggregateId, occurredAt);
                } else if (groupChatEvent instanceof GroupChatEvent.MessageDeleted typedEvent) {
                  deleteMessage(typedEvent.message().getId().asString());
                }
              } catch (SerializationException e) {
                throw new IllegalStateException("Failed to deserialize event", e);
              }
            });
  }

  private static ByteBuffer getByteBuffer(DynamodbEvent.DynamodbStreamRecord record) {
    var attributeValues = getNewImage(record);
    var payloadAttr = attributeValues.get("payload");
    if (payloadAttr == null || payloadAttr.isNULL() != null && payloadAttr.isNULL()) {
      throw new IllegalStateException("Payload is missing or not a binary type");
    }
    return payloadAttr.getB();
  }

  private void deleteMessage(String aggregateId) {
    messageWriteMapper.deleteMessage(aggregateId);
  }

  private void deleteGroupChat(String aggregateId) {
    groupChatWriteMapper.deleteGroupChat(aggregateId);
  }

  private void removeMember(GroupChatEvent.MemberRemoved typedEvent, String aggregateId) {
    memberWriteMapper.deleteMember(aggregateId, typedEvent.member().getUserAccountId().asString());
  }

  private void insertMember(
      GroupChatEvent.MemberAdded typedEvent, String aggregateId, Instant occurredAt) {
    var memberRecord =
        new MemberRecord(
            typedEvent.member().getId().asString(),
            aggregateId,
            typedEvent.member().getUserAccountId().asString(),
            typedEvent.member().getRole().name(),
            occurredAt,
            occurredAt);
    memberWriteMapper.insertMember(memberRecord);
  }

  private void insertMessage(
      GroupChatEvent.MessagePosted typedEvent, String aggregateId, Instant occurredAt) {
    var messageRecord =
        new MessageRecord(
            typedEvent.message().getId().asString(),
            aggregateId,
            typedEvent.message().getContent(),
            typedEvent.message().getSenderId().asString(),
            occurredAt,
            occurredAt);
    messageWriteMapper.insertMessage(messageRecord);
  }

  private void renameGroupChat(
      GroupChatEvent.Renamed typedEvent, String aggregateId, Instant occurredAt) {
    groupChatWriteMapper.updateGroupChatName(aggregateId, typedEvent.name().asString(), occurredAt);
  }

  private void insertGroupChat(
      GroupChatEvent.Created typedEvent, String aggregateId, Instant occurredAt) {
    var groupChatRecord =
        new GroupChatRecord(
            aggregateId,
            typedEvent.name().asString(),
            typedEvent.executorId().asString(),
            occurredAt,
            occurredAt);
    groupChatWriteMapper.insertGroupChat(groupChatRecord);
    var memberId = MemberId.generate();
    var memberRecord =
        new MemberRecord(
            memberId.asString(),
            aggregateId,
            typedEvent.executorId().asString(),
            MemberRole.ADMINISTRATOR.name(),
            occurredAt,
            occurredAt);
    memberWriteMapper.insertMember(memberRecord);
  }

  private static String getAggregateId(DynamodbEvent.DynamodbStreamRecord record) {
    var attributeValues = getNewImage(record);
    var aid = attributeValues.get("aid");
    if (aid == null || aid.getS() == null) {
      throw new IllegalStateException("aid is missing from the stream record");
    }
    return aid.getS();
  }

  private static Instant getOccurredAt(DynamodbEvent.DynamodbStreamRecord record) {
    var attributeValues = getNewImage(record);
    var occurredAt = attributeValues.get("occurred_at");
    if (occurredAt == null || occurredAt.getN() == null) {
      throw new IllegalStateException("occurred_at is missing from the stream record");
    }
    return Instant.ofEpochSecond(0, Long.parseLong(occurredAt.getN()));
  }

  private static Map<String, AttributeValue> getNewImage(
      DynamodbEvent.DynamodbStreamRecord record) {
    var streamRecord = record.getDynamodb();
    if (streamRecord == null) {
      throw new IllegalStateException("streamRecord is null");
    }
    var attributeValues = streamRecord.getNewImage();
    if (attributeValues == null) {
      throw new IllegalStateException("attributeValues is null");
    }
    return attributeValues;
  }
}
