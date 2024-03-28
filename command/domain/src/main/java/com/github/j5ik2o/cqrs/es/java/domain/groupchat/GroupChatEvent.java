package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.f4b6a3.ulid.Ulid;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import com.github.j5ik2o.event.store.adapter.java.Event;
import java.time.Instant;
import javax.annotation.Nonnull;
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "created", value = GroupChatEvent.Created.class),
        @JsonSubTypes.Type(name = "deleted", value = GroupChatEvent.Deleted.class),
        @JsonSubTypes.Type(name = "renamed", value = GroupChatEvent.Renamed.class),
        @JsonSubTypes.Type(name = "memberAdded", value = GroupChatEvent.MemberAdded.class),
        @JsonSubTypes.Type(name = "memberRemoved", value = GroupChatEvent.MemberRemoved.class),
        @JsonSubTypes.Type(name = "messagePosted", value = GroupChatEvent.MessagePosted.class),
        @JsonSubTypes.Type(name = "messageDeleted", value = GroupChatEvent.MessageDeleted.class),
})
public sealed interface GroupChatEvent extends Event<GroupChatId>
    permits GroupChatEvent.Created,
        GroupChatEvent.Deleted,
        GroupChatEvent.Renamed,
        GroupChatEvent.MemberAdded,
        GroupChatEvent.MemberRemoved,
        GroupChatEvent.MessagePosted,
        GroupChatEvent.MessageDeleted {

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

  @JsonTypeName("created")
  @JsonIgnoreProperties(
          value = {"created"},
          allowGetters = true)
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

  @JsonTypeName("deleted")
  @JsonIgnoreProperties(
          value = {"created"},
          allowGetters = true)
  record Deleted(
      Ulid id,
      GroupChatId aggregateId,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}
  @JsonTypeName("renamed")
  @JsonIgnoreProperties(
          value = {"created"},
          allowGetters = true)
  record Renamed(
      Ulid id,
      GroupChatId aggregateId,
      UserAccountId executorId,
      GroupChatName name,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}

  @JsonTypeName("memberAdded")
  @JsonIgnoreProperties(
          value = {"created"},
          allowGetters = true)
  record MemberAdded(
      Ulid id,
      GroupChatId aggregateId,
      Member member,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}

  @JsonTypeName("memberRemoved")
  @JsonIgnoreProperties(
          value = {"created"},
          allowGetters = true)
  record MemberRemoved(
      Ulid id,
      GroupChatId aggregateId,
      Member member,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}

  @JsonTypeName("messagePosted")
  @JsonIgnoreProperties(
          value = {"created"},
          allowGetters = true)
  record MessagePosted(
      Ulid id,
      GroupChatId aggregateId,
      Message message,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}

  @JsonTypeName("messageDeleted")
  @JsonIgnoreProperties(
          value = {"created"},
          allowGetters = true)
  record MessageDeleted(
      Ulid id,
      GroupChatId aggregateId,
      Message message,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {}
}
