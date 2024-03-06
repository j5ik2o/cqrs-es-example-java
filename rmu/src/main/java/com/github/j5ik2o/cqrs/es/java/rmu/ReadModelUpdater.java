package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatEventSerializer;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.MemberId;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.*;
import com.github.j5ik2o.event.store.adapter.java.DeserializationException;
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
              var payloadBytes = payloadAttr.getB();
              try {
                GroupChatEvent groupChatEvent =
                    serializer.deserialize(payloadBytes.array(), GroupChatEvent.class);
                if (groupChatEvent instanceof GroupChatEvent.Created typedEvent) {
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
                } else if (groupChatEvent instanceof GroupChatEvent.Deleted typedEvent) {
                  groupChatMapper.deleteGroupChat(typedEvent.aggregateId().asString());
                } else if (groupChatEvent instanceof GroupChatEvent.Renamed typedEvent) {
                  var groupChatRecord =
                      new GroupChatRecord(
                          typedEvent.aggregateId().asString(),
                          typedEvent.name().asString(),
                          typedEvent.executorId().asString(),
                          typedEvent.occurredAt(),
                          typedEvent.occurredAt());
                  groupChatMapper.updateGroupChat(groupChatRecord);
                } else if (groupChatEvent instanceof GroupChatEvent.MemberAdded typedEvent) {
                  var memberId = MemberId.generate();
                  var memberRecord =
                      new MemberRecord(
                          memberId.asString(),
                          typedEvent.aggregateId().asString(),
                          typedEvent.executorId().asString(),
                          typedEvent.occurredAt(),
                          typedEvent.occurredAt());
                  memberMapper.insertMember(memberRecord);
                } else if (groupChatEvent instanceof GroupChatEvent.MemberRemoved typedEvent) {
                  memberMapper.deleteMember(
                      typedEvent.aggregateId().asString(),
                      typedEvent.member().getUserAccountId().asString());
                } else if (groupChatEvent instanceof GroupChatEvent.MessagePosted typedEvent) {
                  var messageRecord =
                      new MessageRecord(
                          typedEvent.getId(),
                          typedEvent.aggregateId().asString(),
                          typedEvent.message().getContent(),
                          typedEvent.message().getSenderId().asString(),
                          typedEvent.occurredAt(),
                          typedEvent.occurredAt());
                  messageMapper.insertMessage(messageRecord);
                } else if (groupChatEvent instanceof GroupChatEvent.MessageDeleted typedEvent) {
                  messageMapper.deleteMessage(typedEvent.aggregateId().asString());
                }
              } catch (DeserializationException e) {
                throw new IllegalStateException("Failed to deserialize event", e);
              }
            });
  }
}
