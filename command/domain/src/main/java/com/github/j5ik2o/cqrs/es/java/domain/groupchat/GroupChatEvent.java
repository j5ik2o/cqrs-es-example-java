package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.github.f4b6a3.ulid.Ulid;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import com.github.j5ik2o.event.store.adapter.java.Event;
import java.time.Instant;
import javax.annotation.Nonnull;

public sealed interface GroupChatEvent extends Event<GroupChatId>
    permits GroupChatEvent.Created,
        GroupChatEvent.Deleted,
        GroupChatEvent.Renamed,
        GroupChatEvent.MemberAdded,
        GroupChatEvent.MemberRemoved,
GroupChatEvent.MessagePosted, GroupChatEvent.MessageRemoved {

  @Nonnull
  Ulid id();

  @Nonnull
  @Override
  default String getId() {
    return id().toString();
  }

  @Nonnull
  GroupChatId aggregateId();

  @Nonnull
  @Override
  default GroupChatId getAggregateId() {
    return aggregateId();
  }

  @Nonnull
  UserAccountId executorId();

  @Nonnull
  default UserAccountId getExecutorId() {
    return executorId();
  }

  long sequenceNumber();

  @Override
  default long getSequenceNumber() {
    return sequenceNumber();
  }

  @Nonnull
  Instant occurredAt();

  @Nonnull
  @Override
  default Instant getOccurredAt() {
    return occurredAt();
  }

  default boolean isCreated() {
    return false;
  }

  record Created(
      Ulid id,
      GroupChatId aggregateId,
      UserAccountId executorId,
      GroupChatName name,
      Members members,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {

    @Override
    public boolean isCreated() {
      return true;
    }
  }

  record Deleted(
      Ulid id,
      GroupChatId aggregateId,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}

  record Renamed(
      Ulid id,
      GroupChatId aggregateId,
      UserAccountId executorId,
      GroupChatName name,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}

  record MemberAdded(
      Ulid id,
      GroupChatId aggregateId,
      Member member,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}

  record MemberRemoved(
      Ulid id,
      GroupChatId aggregateId,
      Member member,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}

  record MessagePosted(
      Ulid id,
      GroupChatId aggregateId,
      Message message,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}

  record MessageRemoved(
      Ulid id,
      GroupChatId aggregateId,
      Message message,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}
}
