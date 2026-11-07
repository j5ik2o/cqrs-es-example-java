package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.f4b6a3.ulid.Ulid;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import java.time.Instant;
import javax.annotation.Nonnull;

/**
 * Payload of a domain event. Envelope metadata such as the aggregate id, sequence number and
 * occurrence time stays in the envelope and is not serialized into the payload.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(name = GroupChatEvent.CREATED_TYPE, value = GroupChatEvent.Created.class),
  @JsonSubTypes.Type(name = GroupChatEvent.DELETED_TYPE, value = GroupChatEvent.Deleted.class),
  @JsonSubTypes.Type(name = GroupChatEvent.RENAMED_TYPE, value = GroupChatEvent.Renamed.class),
  @JsonSubTypes.Type(
      name = GroupChatEvent.MEMBER_ADDED_TYPE,
      value = GroupChatEvent.MemberAdded.class),
  @JsonSubTypes.Type(
      name = GroupChatEvent.MEMBER_REMOVED_TYPE,
      value = GroupChatEvent.MemberRemoved.class),
  @JsonSubTypes.Type(
      name = GroupChatEvent.MESSAGE_POSTED_TYPE,
      value = GroupChatEvent.MessagePosted.class),
  @JsonSubTypes.Type(
      name = GroupChatEvent.MESSAGE_EDITED_TYPE,
      value = GroupChatEvent.MessageEdited.class),
  @JsonSubTypes.Type(
      name = GroupChatEvent.MESSAGE_DELETED_TYPE,
      value = GroupChatEvent.MessageDeleted.class),
})
@JsonIgnoreProperties({"aggregateId", "sequenceNumber", "occurredAt", "created"})
public sealed interface GroupChatEvent
    permits GroupChatEvent.Created,
        GroupChatEvent.Deleted,
        GroupChatEvent.Renamed,
        GroupChatEvent.MemberAdded,
        GroupChatEvent.MemberRemoved,
        GroupChatEvent.MessagePosted,
        GroupChatEvent.MessageEdited,
        GroupChatEvent.MessageDeleted {

  int SCHEMA_VERSION = 1;

  String CREATED_TYPE = "GroupChatCreated";
  String DELETED_TYPE = "GroupChatDeleted";
  String RENAMED_TYPE = "GroupChatRenamed";
  String MEMBER_ADDED_TYPE = "GroupChatMemberAdded";
  String MEMBER_REMOVED_TYPE = "GroupChatMemberRemoved";
  String MESSAGE_POSTED_TYPE = "GroupChatMessagePosted";
  String MESSAGE_EDITED_TYPE = "GroupChatMessageEdited";
  String MESSAGE_DELETED_TYPE = "GroupChatMessageDeleted";

  @Nonnull
  Ulid id();

  @Nonnull
  default String getId() {
    return id().toString();
  }

  @Nonnull
  GroupChatId aggregateId();

  @Nonnull
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

  default long getSequenceNumber() {
    return sequenceNumber();
  }

  @Nonnull
  Instant occurredAt();

  @Nonnull
  default Instant getOccurredAt() {
    return occurredAt();
  }

  default boolean isCreated() {
    return false;
  }

  /** The event type name carried by the envelope manifest. */
  @JsonIgnore
  @Nonnull
  String typeName();

  /** The envelope manifest in the form {@code <event-type>@<schema-version>}. */
  @JsonIgnore
  @Nonnull
  default String manifest() {
    return String.format("%s@%d", typeName(), SCHEMA_VERSION);
  }

  @JsonTypeName(CREATED_TYPE)
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

    @Override
    public String typeName() {
      return CREATED_TYPE;
    }
  }

  @JsonTypeName(DELETED_TYPE)
  record Deleted(
      Ulid id,
      GroupChatId aggregateId,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {

    @Override
    public String typeName() {
      return DELETED_TYPE;
    }
  }

  @JsonTypeName(RENAMED_TYPE)
  record Renamed(
      Ulid id,
      GroupChatId aggregateId,
      UserAccountId executorId,
      GroupChatName name,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {

    @Override
    public String typeName() {
      return RENAMED_TYPE;
    }
  }

  @JsonTypeName(MEMBER_ADDED_TYPE)
  record MemberAdded(
      Ulid id,
      GroupChatId aggregateId,
      Member member,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {

    @Override
    public String typeName() {
      return MEMBER_ADDED_TYPE;
    }
  }

  @JsonTypeName(MEMBER_REMOVED_TYPE)
  record MemberRemoved(
      Ulid id,
      GroupChatId aggregateId,
      Member member,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {

    @Override
    public String typeName() {
      return MEMBER_REMOVED_TYPE;
    }
  }

  @JsonTypeName(MESSAGE_POSTED_TYPE)
  record MessagePosted(
      Ulid id,
      GroupChatId aggregateId,
      Message message,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {

    @Override
    public String typeName() {
      return MESSAGE_POSTED_TYPE;
    }
  }

  @JsonTypeName(MESSAGE_EDITED_TYPE)
  record MessageEdited(
      Ulid id,
      GroupChatId aggregateId,
      Message message,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {

    @Override
    public String typeName() {
      return MESSAGE_EDITED_TYPE;
    }
  }

  @JsonTypeName(MESSAGE_DELETED_TYPE)
  record MessageDeleted(
      Ulid id,
      GroupChatId aggregateId,
      Message message,
      UserAccountId executorId,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {

    @Override
    public String typeName() {
      return MESSAGE_DELETED_TYPE;
    }
  }
}
