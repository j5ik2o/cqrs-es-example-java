package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatEventSerializer;
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
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared fixtures for the read model updater tests. */
final class RmuTestSupport {

  static final Instant BASE_TIME = Instant.parse("2026-11-07T12:00:00Z");
  static final String STORE_ID = "store-0001";
  private static final GroupChatEventSerializer SERIALIZER = new GroupChatEventSerializer();

  private RmuTestSupport() {}

  static Fixture createFixture() {
    var groupChatId = GroupChatId.generate();
    var adminId = UserAccountId.generate();
    var memberId = UserAccountId.generate();
    var createResult = GroupChat.create(groupChatId, GroupChatName.of("group-chat"), adminId);
    var created = createResult._2;
    var groupChat = createResult._1;

    var memberAddedResult =
        groupChat.addMember(Member.of(MemberId.generate(), memberId, MemberRole.MEMBER), adminId);
    var memberAdded = memberAddedResult.get()._2;
    var groupChatWithMember = memberAddedResult.get()._1;

    var messageId = MessageId.generate();
    var message = Message.of(messageId, "hello", adminId, LocalDateTime.now());
    var messagePostedResult = groupChatWithMember.postMessage(message, adminId);
    var messagePosted = messagePostedResult.get()._2;
    var groupChatWithMessage = messagePostedResult.get()._1;

    var messageEdited = groupChatWithMessage.editMessage(messageId, "edited", adminId).get()._2;
    return new Fixture(
        groupChatId,
        adminId,
        memberId,
        messageId,
        created,
        memberAdded,
        messagePosted,
        messageEdited);
  }

  /** Builds a journal with {@code count} events: create, add member, then messages. */
  static EventLog createEvents(int count) {
    if (count < 2) {
      throw new IllegalArgumentException("count must be at least 2");
    }
    var groupChatId = GroupChatId.generate();
    var adminId = UserAccountId.generate();
    var createResult = GroupChat.create(groupChatId, GroupChatName.of("group-chat"), adminId);
    var groupChat = createResult._1;
    var events = new ArrayList<HeadEvent>();
    events.add(headEvent(1, createResult._2));

    var memberId = UserAccountId.generate();
    var memberAddedResult =
        groupChat.addMember(Member.of(MemberId.generate(), memberId, MemberRole.MEMBER), adminId);
    groupChat = memberAddedResult.get()._1;
    events.add(headEvent(2, memberAddedResult.get()._2));

    for (var seqNr = 3; seqNr <= count; seqNr++) {
      var message =
          Message.of(MessageId.generate(), "message-" + seqNr, adminId, LocalDateTime.now());
      var posted = groupChat.postMessage(message, adminId);
      groupChat = posted.get()._1;
      events.add(headEvent(seqNr, posted.get()._2));
    }
    return new EventLog(groupChatId.asString(), List.copyOf(events));
  }

  static HeadEvent headEvent(long seqNr, GroupChatEvent event) {
    return new HeadEvent(
        seqNr, BASE_TIME.plusSeconds(seqNr), event.manifest(), SERIALIZER.serialize(event));
  }

  static HeadEvent invalidHeadEvent(long seqNr) {
    return new HeadEvent(
        seqNr,
        BASE_TIME.plusSeconds(seqNr),
        "GroupChatMessagePosted@1",
        "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  static DynamodbEvent headRecord(String aggregateId, String eventName, List<HeadEvent> events) {
    var newImage = new LinkedHashMap<String, AttributeValue>();
    newImage.put("aid", stringValue(aggregateId));
    newImage.put("type_name", stringValue("GroupChat"));
    if (events != null) {
      var seqNr = events.stream().mapToLong(HeadEvent::seqNr).max().orElse(0L);
      newImage.put("seq_nr", numberValue(seqNr));
      var eventList = new ArrayList<AttributeValue>();
      for (var event : events) {
        var fields = new LinkedHashMap<String, AttributeValue>();
        fields.put("seq_nr", numberValue(event.seqNr()));
        fields.put(
            "occurred_at",
            numberValue(
                event.occurredAt().getEpochSecond() * 1_000_000_000L
                    + event.occurredAt().getNano()));
        fields.put("manifest", stringValue(event.manifest()));
        var payload = new AttributeValue();
        payload.setB(ByteBuffer.wrap(event.payload()));
        fields.put("payload", payload);
        var item = new AttributeValue();
        item.setM(fields);
        eventList.add(item);
      }
      var eventsAttribute = new AttributeValue();
      eventsAttribute.setL(eventList);
      newImage.put("events", eventsAttribute);
    }
    return record(aggregateId, eventName, newImage);
  }

  static DynamodbEvent record(
      String aggregateId, String eventName, Map<String, AttributeValue> newImage) {
    var streamRecord =
        new com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord();
    var keys = new LinkedHashMap<String, AttributeValue>();
    keys.put("aid", stringValue(aggregateId));
    streamRecord.setKeys(keys);
    streamRecord.setNewImage(newImage);
    var record = new DynamodbEvent.DynamodbStreamRecord();
    record.setEventName(eventName);
    record.setEventID("event-" + aggregateId + "-" + eventName);
    record.setDynamodb(streamRecord);
    var event = new DynamodbEvent();
    event.setRecords(List.of(record));
    return event;
  }

  static AttributeValue stringValue(String value) {
    var attribute = new AttributeValue();
    attribute.setS(value);
    return attribute;
  }

  static AttributeValue numberValue(long value) {
    var attribute = new AttributeValue();
    attribute.setN(Long.toString(value));
    return attribute;
  }

  static final class InMemoryJournalReader implements JournalReader {
    private final Map<String, Map<Long, HeadEvent>> events = new HashMap<>();

    void put(String aggregateId, HeadEvent event) {
      events.computeIfAbsent(aggregateId, key -> new HashMap<>()).put(event.seqNr(), event);
    }

    void putAll(String aggregateId, List<HeadEvent> headEvents) {
      for (var event : headEvents) {
        put(aggregateId, event);
      }
    }

    @Override
    public List<HeadEvent> readEvents(String aggregateId, long fromSeqNr, long toSeqNr) {
      return events.getOrDefault(aggregateId, Map.of()).values().stream()
          .filter(event -> event.seqNr() >= fromSeqNr && event.seqNr() <= toSeqNr)
          .sorted(Comparator.comparingLong(HeadEvent::seqNr))
          .toList();
    }
  }

  static final class InMemoryRecoveryTaskStore implements RecoveryTaskStore {
    private final List<RecoveryTask> pending = new ArrayList<>();

    @Override
    public void record(RecoveryTask task) {
      pending.removeIf(
          current ->
              current.storeId().equals(task.storeId())
                  && current.projectionName().equals(task.projectionName())
                  && current.aggregateId().equals(task.aggregateId())
                  && current.seqNr() == task.seqNr());
      pending.add(task);
    }

    @Override
    public List<RecoveryTask> listPending() {
      return List.copyOf(pending);
    }

    @Override
    public void markCompleted(
        String storeId, String projectionName, String aggregateId, long seqNr) {
      pending.removeIf(
          task ->
              task.storeId().equals(storeId)
                  && task.projectionName().equals(projectionName)
                  && task.aggregateId().equals(aggregateId)
                  && task.seqNr() == seqNr);
    }
  }

  record EventLog(String aggregateId, List<HeadEvent> events) {}

  record Fixture(
      GroupChatId groupChatId,
      UserAccountId adminId,
      UserAccountId memberId,
      MessageId messageId,
      GroupChatEvent created,
      GroupChatEvent memberAdded,
      GroupChatEvent messagePosted,
      GroupChatEvent messageEdited) {
    String aid() {
      return groupChatId.asString();
    }
  }
}
