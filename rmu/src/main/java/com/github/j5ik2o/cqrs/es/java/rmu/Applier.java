package com.github.j5ik2o.cqrs.es.java.rmu;

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
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Applies journal and head events in bounded transactions.
 *
 * <p>Only {@code position + 1} is applied. The position row is locked with {@code SELECT ... FOR
 * UPDATE} and the read model change and the position advance commit in the same transaction, so a
 * restart, a retried batch, or two concurrent readers cannot skip or apply an event twice. A gap in
 * the journal fails without advancing the position.
 */
public final class Applier implements EventApplier {
  private static final int DEFAULT_READ_AHEAD_EVENTS = 100;

  private final SqlSessionFactory sqlSessionFactory;
  private final JournalReader journalReader;
  private final int maxEventsPerTransaction;
  private final int readAheadEvents;
  private final GroupChatEventSerializer serializer = new GroupChatEventSerializer();

  public Applier(SqlSessionFactory sqlSessionFactory, JournalReader journalReader) {
    this(sqlSessionFactory, journalReader, 1);
  }

  public Applier(
      SqlSessionFactory sqlSessionFactory,
      JournalReader journalReader,
      int maxEventsPerTransaction) {
    this.sqlSessionFactory = sqlSessionFactory;
    this.journalReader = journalReader;
    this.maxEventsPerTransaction = Math.max(1, maxEventsPerTransaction);
    this.readAheadEvents = DEFAULT_READ_AHEAD_EVENTS;
  }

  @Override
  public long position(String aggregateId) {
    try (var session = sqlSessionFactory.openSession(true)) {
      var position = session.getMapper(ReadModelPositionMapper.class).selectSeqNr(aggregateId);
      return position == null ? 0L : position;
    }
  }

  @Override
  public void applyHeadEvent(String aggregateId, HeadEvent event) {
    var groupChatId = parseAggregateId(aggregateId);
    var currentPosition = position(aggregateId);
    if (event.seqNr() <= currentPosition) {
      return;
    }
    if (event.seqNr() > currentPosition + 1) {
      if (journalReader == null) {
        throw PermanentFailure.permanent(
            FailureKind.JOURNAL_GAP,
            "aggregate %s: seq_nr %d follows position %d and no journal reader is configured"
                .formatted(aggregateId, event.seqNr(), currentPosition));
      }
      // Read and apply the gap in bounded chunks. A head far ahead of the position must not
      // accumulate every missing event in memory before anything is committed.
      applyThrough(aggregateId, event.seqNr() - 1);
    }
    applyEvents(aggregateId, groupChatId, List.of(event));
  }

  @Override
  public void applyThrough(String aggregateId, long targetSeqNr) {
    if (targetSeqNr <= 0) {
      return;
    }
    var groupChatId = parseAggregateId(aggregateId);
    if (journalReader == null) {
      throw PermanentFailure.permanent(
          FailureKind.JOURNAL_GAP,
          "aggregate %s: journal reader is required to reach seq_nr %d"
              .formatted(aggregateId, targetSeqNr));
    }
    while (true) {
      var currentPosition = position(aggregateId);
      if (currentPosition >= targetSeqNr) {
        return;
      }
      var endSeqNr = Math.min(currentPosition + readAheadEvents, targetSeqNr);
      var journalEvents = journalReader.readEvents(aggregateId, currentPosition + 1, endSeqNr);
      requireContiguous(currentPosition + 1, endSeqNr, journalEvents);
      applyEvents(aggregateId, groupChatId, journalEvents);
      var after = position(aggregateId);
      if (after <= currentPosition) {
        throw new ReadModelUpdateException(
            "aggregate %s: position stayed at %d while replaying to %d"
                .formatted(aggregateId, after, targetSeqNr));
      }
    }
  }

  private void applyEvents(String aggregateId, GroupChatId groupChatId, List<HeadEvent> events) {
    for (var from = 0; from < events.size(); from += maxEventsPerTransaction) {
      var to = Math.min(from + maxEventsPerTransaction, events.size());
      var batch = events.subList(from, to);
      processInTransaction(
          aggregateId,
          (session, current) -> {
            var next = current;
            for (var event : batch) {
              if (event.seqNr() <= next) {
                continue;
              }
              if (event.seqNr() != next + 1) {
                throw PermanentFailure.permanent(
                    FailureKind.JOURNAL_GAP,
                    "expected seq_nr " + (next + 1) + " but got " + event.seqNr());
              }
              applyEvent(groupChatId, event, session);
              next = event.seqNr();
            }
            return next;
          });
    }
  }

  private void processInTransaction(String aggregateId, PositionedWork work) {
    var maxAttempts = 5;
    for (var attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        doProcessInTransaction(aggregateId, work);
        return;
      } catch (RuntimeException e) {
        if (attempt == maxAttempts || !isTransactionConflict(e)) {
          throw e;
        }
        sleepQuietly(5L * attempt);
      }
    }
  }

  private void doProcessInTransaction(String aggregateId, PositionedWork work) {
    try (var session = sqlSessionFactory.openSession(false)) {
      try {
        var connection = session.getConnection();
        var originalAutoCommit = connection.getAutoCommit();
        if (originalAutoCommit) {
          // MyBatis Spring's SpringManagedTransaction does not start a transaction when no Spring
          // transaction is active, so openSession(false) alone is not enough. Drive the JDBC
          // connection so the position row and the read model change really commit together.
          connection.setAutoCommit(false);
        }
        try {
          var positionMapper = session.getMapper(ReadModelPositionMapper.class);
          positionMapper.insertIfAbsent(aggregateId, Instant.now());
          var currentValue = positionMapper.selectSeqNrForUpdate(aggregateId);
          var current = currentValue == null ? 0L : currentValue;
          var next = work.apply(session, current);
          if (next != current) {
            positionMapper.upsert(aggregateId, next, Instant.now());
          }
          connection.commit();
        } catch (SQLException e) {
          rollbackQuietly(connection, e);
          throw new ReadModelUpdateException("failed to commit the read model transaction", e);
        } catch (RuntimeException | Error e) {
          rollbackQuietly(connection, e);
          throw e;
        } finally {
          if (originalAutoCommit) {
            try {
              connection.setAutoCommit(true);
            } catch (SQLException ignored) {
              // The pool resets the connection when the session closes.
            }
          }
        }
      } catch (SQLException e) {
        throw new ReadModelUpdateException("failed to manage the read model transaction", e);
      }
    }
  }

  private static void rollbackQuietly(java.sql.Connection connection, Throwable failure) {
    try {
      connection.rollback();
    } catch (SQLException rollbackError) {
      failure.addSuppressed(rollbackError);
    }
  }

  /**
   * A deadlock or lock-wait timeout means another transaction held the position row. Retrying the
   * whole transaction is safe because the failed transaction rolled back.
   */
  private static boolean isTransactionConflict(Throwable failure) {
    for (var cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof java.sql.SQLException sqlException
          && (sqlException.getErrorCode() == 1213 || sqlException.getErrorCode() == 1205)) {
        return true;
      }
    }
    return false;
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RetryInterruptedException(e);
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
      throw PermanentFailure.permanent(
          FailureKind.UNSUPPORTED_EVENT, "unsupported event type " + groupChatEvent.typeName());
    }
  }

  private GroupChatEvent deserialize(HeadEvent headEvent) {
    try {
      var event = serializer.deserialize(headEvent.payload());
      if (event == null) {
        throw PermanentFailure.permanent(
            FailureKind.EVENT_PAYLOAD,
            "failed to deserialize event with seq_nr " + headEvent.seqNr());
      }
      return event;
    } catch (SerializationException e) {
      throw PermanentFailure.permanent(
          FailureKind.EVENT_PAYLOAD,
          "failed to deserialize event with seq_nr " + headEvent.seqNr(),
          e);
    }
  }

  private static GroupChatId parseAggregateId(String aggregateId) {
    try {
      return GroupChatId.ofString(aggregateId);
    } catch (RuntimeException e) {
      throw PermanentFailure.permanent(
          FailureKind.EVENT_PAYLOAD, "invalid aggregate id " + aggregateId, e);
    }
  }

  private static void requireContiguous(long fromSeqNr, long toSeqNr, List<HeadEvent> events) {
    var expected = fromSeqNr;
    for (var event : events) {
      if (event.seqNr() != expected) {
        throw PermanentFailure.permanent(
            FailureKind.JOURNAL_GAP,
            "journal gap: expected seq_nr " + expected + " but got " + event.seqNr());
      }
      expected++;
    }
    if (expected != toSeqNr + 1) {
      throw PermanentFailure.permanent(
          FailureKind.JOURNAL_GAP, "journal is missing seq_nr " + expected + ".." + toSeqNr);
    }
  }

  @FunctionalInterface
  private interface PositionedWork {
    long apply(SqlSession session, long position);
  }
}
