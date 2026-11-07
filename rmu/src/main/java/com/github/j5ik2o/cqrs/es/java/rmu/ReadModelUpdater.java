package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatEventSerializer;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.GroupChatRecord;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.GroupChatWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.MemberRecord;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.MemberWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.MessageRecord;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.MessageWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.ReadModelPositionMapper;
import com.github.j5ik2o.event.store.adapter.java.core.SerializationException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Builds the read model from the head table's DynamoDB Streams.
 *
 * <p>Each head stream record carries the head item's {@code events} list, which holds the events of
 * one write transaction. Duplicate and stale sequence numbers are dropped, a gap in {@code seq_nr}
 * is filled from the journal, and the aggregate's position is written in the same database
 * transaction as the read model change.
 */
@Component
@Profile("local-rmu")
public class ReadModelUpdater {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReadModelUpdater.class);
  private static final String CONFIG_AGGREGATE_ID = "__config__";
  private static final String GROUP_CHAT_ID_PREFIX = "GroupChat-";
  private static final String EVENT_NAME_INSERT = "INSERT";
  private static final String EVENT_NAME_MODIFY = "MODIFY";
  private static final String EVENT_NAME_REMOVE = "REMOVE";

  private final SqlSessionFactory sqlSessionFactory;
  private final JournalReader journalReader;
  private final GroupChatEventSerializer serializer = new GroupChatEventSerializer();

  public ReadModelUpdater(SqlSessionFactory sqlSessionFactory, JournalReader journalReader) {
    this.sqlSessionFactory = sqlSessionFactory;
    this.journalReader = journalReader;
  }

  public void update(DynamodbEvent dynamodbEvent) {
    for (var record : dynamodbEvent.getRecords()) {
      applyRecord(record);
    }
  }

  private void applyRecord(DynamodbEvent.DynamodbStreamRecord record) {
    var eventName = record.getEventName();
    if (EVENT_NAME_REMOVE.equals(eventName)) {
      return;
    }
    if (!EVENT_NAME_INSERT.equals(eventName) && !EVENT_NAME_MODIFY.equals(eventName)) {
      throw new ReadModelUpdateException("unexpected stream event name " + eventName);
    }
    var image = getNewImage(record);
    var aggregateId = getStringAttribute(image, "aid");
    if (CONFIG_AGGREGATE_ID.equals(aggregateId)) {
      return;
    }
    if (!aggregateId.startsWith(GROUP_CHAT_ID_PREFIX)) {
      LOGGER.debug("Skipping head record of another aggregate type: aid={}", aggregateId);
      return;
    }
    for (var headEvent : parseHeadEvents(image)) {
      applyHeadEvent(aggregateId, headEvent);
    }
  }

  private void applyHeadEvent(String aggregateId, HeadEvent headEvent) {
    var groupChatId = GroupChatId.ofString(aggregateId);
    var position = currentPosition(aggregateId);
    if (headEvent.seqNr() <= position) {
      LOGGER.debug(
          "Skipping duplicate or stale event: aid={}, seqNr={}, position={}",
          aggregateId,
          headEvent.seqNr(),
          position);
      return;
    }

    var pending = new ArrayList<HeadEvent>();
    if (headEvent.seqNr() > position + 1) {
      var journalEvents =
          journalReader.readEvents(aggregateId, position + 1, headEvent.seqNr() - 1);
      requireContiguous(position + 1, headEvent.seqNr() - 1, journalEvents);
      pending.addAll(journalEvents);
    }
    pending.add(headEvent);

    processInTransaction(
        aggregateId,
        (session, current) -> {
          var next = current;
          for (var event : pending) {
            if (event.seqNr() <= next) {
              continue;
            }
            if (event.seqNr() != next + 1) {
              throw new ReadModelUpdateException(
                  "expected seq_nr " + (next + 1) + " but got " + event.seqNr());
            }
            applyEvent(groupChatId, event, session);
            next = event.seqNr();
          }
          return next;
        });
  }

  private long currentPosition(String aggregateId) {
    try (var session = sqlSessionFactory.openSession(true)) {
      var position = session.getMapper(ReadModelPositionMapper.class).selectSeqNr(aggregateId);
      return position == null ? 0L : position;
    }
  }

  private void processInTransaction(String aggregateId, PositionedWork work) {
    try (var session = sqlSessionFactory.openSession(false)) {
      try {
        var positionMapper = session.getMapper(ReadModelPositionMapper.class);
        positionMapper.insertIfAbsent(aggregateId, Instant.now());
        var currentValue = positionMapper.selectSeqNrForUpdate(aggregateId);
        var current = currentValue == null ? 0L : currentValue;
        var next = work.apply(session, current);
        if (next != current) {
          positionMapper.upsert(aggregateId, next, Instant.now());
        }
        session.commit();
      } catch (RuntimeException | Error e) {
        try {
          session.rollback();
        } catch (RuntimeException rollbackError) {
          e.addSuppressed(rollbackError);
        }
        throw e;
      }
    }
  }

  private void applyEvent(GroupChatId aggregateId, HeadEvent headEvent, SqlSession session) {
    var groupChatEvent = deserialize(headEvent);
    var occurredAt = headEvent.occurredAt();
    var groupChatWriteMapper = session.getMapper(GroupChatWriteMapper.class);
    var memberWriteMapper = session.getMapper(MemberWriteMapper.class);
    var messageWriteMapper = session.getMapper(MessageWriteMapper.class);

    if (groupChatEvent instanceof GroupChatEvent.Created typedEvent) {
      groupChatWriteMapper.insertGroupChat(
          new GroupChatRecord(
              aggregateId.asString(),
              typedEvent.name().asString(),
              typedEvent.executorId().asString(),
              occurredAt,
              occurredAt));
      var administrator = typedEvent.members().toVector().head();
      memberWriteMapper.insertMember(
          new MemberRecord(
              administrator.getId().asString(),
              aggregateId.asString(),
              administrator.getUserAccountId().asString(),
              administrator.getRole().name(),
              occurredAt,
              occurredAt));
    } else if (groupChatEvent instanceof GroupChatEvent.Deleted) {
      groupChatWriteMapper.deleteGroupChat(aggregateId.asString(), occurredAt);
    } else if (groupChatEvent instanceof GroupChatEvent.Renamed typedEvent) {
      groupChatWriteMapper.updateGroupChatName(
          aggregateId.asString(), typedEvent.name().asString(), occurredAt);
    } else if (groupChatEvent instanceof GroupChatEvent.MemberAdded typedEvent) {
      memberWriteMapper.insertMember(
          new MemberRecord(
              typedEvent.member().getId().asString(),
              aggregateId.asString(),
              typedEvent.member().getUserAccountId().asString(),
              typedEvent.member().getRole().name(),
              occurredAt,
              occurredAt));
    } else if (groupChatEvent instanceof GroupChatEvent.MemberRemoved typedEvent) {
      memberWriteMapper.deleteMember(
          aggregateId.asString(), typedEvent.member().getUserAccountId().asString());
    } else if (groupChatEvent instanceof GroupChatEvent.MessagePosted typedEvent) {
      messageWriteMapper.insertMessage(
          new MessageRecord(
              typedEvent.message().getId().asString(),
              aggregateId.asString(),
              typedEvent.message().getSenderId().asString(),
              typedEvent.message().getContent(),
              occurredAt,
              occurredAt));
    } else if (groupChatEvent instanceof GroupChatEvent.MessageEdited typedEvent) {
      messageWriteMapper.updateMessage(
          new MessageRecord(
              typedEvent.message().getId().asString(),
              aggregateId.asString(),
              typedEvent.message().getSenderId().asString(),
              typedEvent.message().getContent(),
              occurredAt,
              occurredAt));
    } else if (groupChatEvent instanceof GroupChatEvent.MessageDeleted typedEvent) {
      messageWriteMapper.deleteMessage(typedEvent.message().getId().asString(), occurredAt);
    } else {
      throw new ReadModelUpdateException("unsupported event type " + groupChatEvent.typeName());
    }
  }

  private GroupChatEvent deserialize(HeadEvent headEvent) {
    try {
      var event = serializer.deserialize(headEvent.payload());
      if (event == null) {
        throw new ReadModelUpdateException(
            "failed to deserialize event with seq_nr " + headEvent.seqNr());
      }
      return event;
    } catch (SerializationException e) {
      throw new ReadModelUpdateException(
          "failed to deserialize event with seq_nr " + headEvent.seqNr(), e);
    }
  }

  private static void requireContiguous(long fromSeqNr, long toSeqNr, List<HeadEvent> events) {
    var expected = fromSeqNr;
    for (var event : events) {
      if (event.seqNr() != expected) {
        throw new ReadModelUpdateException(
            "journal gap: expected seq_nr " + expected + " but got " + event.seqNr());
      }
      expected++;
    }
    if (expected != toSeqNr + 1) {
      throw new ReadModelUpdateException("journal is missing seq_nr " + expected + ".." + toSeqNr);
    }
  }

  private static List<HeadEvent> parseHeadEvents(Map<String, AttributeValue> image) {
    var eventsAttribute = image.get("events");
    if (eventsAttribute == null || eventsAttribute.getL() == null) {
      throw new ReadModelUpdateException("head image has no events attribute");
    }
    var events = new ArrayList<HeadEvent>();
    for (var item : eventsAttribute.getL()) {
      var fields = item.getM();
      if (fields == null) {
        throw new ReadModelUpdateException("head event is not a map");
      }
      events.add(
          new HeadEvent(
              getNumberAttribute(fields, "seq_nr"),
              Instant.ofEpochSecond(0, getNumberAttribute(fields, "occurred_at")),
              getOptionalStringAttribute(fields, "manifest"),
              getPayload(fields)));
    }
    events.sort(Comparator.comparingLong(HeadEvent::seqNr));
    return events;
  }

  private static Map<String, AttributeValue> getNewImage(
      DynamodbEvent.DynamodbStreamRecord record) {
    var streamRecord = record.getDynamodb();
    if (streamRecord == null) {
      throw new ReadModelUpdateException("streamRecord is null");
    }
    var attributeValues = streamRecord.getNewImage();
    if (attributeValues == null) {
      throw new ReadModelUpdateException("new image is null");
    }
    return attributeValues;
  }

  private static String getStringAttribute(Map<String, AttributeValue> attributes, String name) {
    var attribute = attributes.get(name);
    if (attribute == null || attribute.getS() == null) {
      throw new ReadModelUpdateException("attribute " + name + " is missing or not a string");
    }
    return attribute.getS();
  }

  private static String getOptionalStringAttribute(
      Map<String, AttributeValue> attributes, String name) {
    var attribute = attributes.get(name);
    return attribute == null || attribute.getS() == null ? "" : attribute.getS();
  }

  private static long getNumberAttribute(Map<String, AttributeValue> attributes, String name) {
    var attribute = attributes.get(name);
    if (attribute == null || attribute.getN() == null) {
      throw new ReadModelUpdateException("attribute " + name + " is missing or not a number");
    }
    return Long.parseLong(attribute.getN());
  }

  private static byte[] getPayload(Map<String, AttributeValue> attributes) {
    var attribute = attributes.get("payload");
    if (attribute == null) {
      throw new ReadModelUpdateException("head event has no payload");
    }
    if (attribute.getB() != null) {
      ByteBuffer buffer = attribute.getB().duplicate();
      var bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      return bytes;
    }
    if (attribute.getS() != null) {
      return attribute.getS().getBytes(StandardCharsets.UTF_8);
    }
    throw new ReadModelUpdateException("head event has no payload");
  }

  @FunctionalInterface
  private interface PositionedWork {
    long apply(SqlSession session, long position);
  }
}
