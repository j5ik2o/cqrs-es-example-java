package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.github.f4b6a3.ulid.UlidCreator;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import com.github.j5ik2o.event.store.adapter.java.Aggregate;
import io.vavr.Tuple2;
import java.time.Instant;
import javax.annotation.Nonnull;

public class GroupChat implements Aggregate<GroupChat, GroupChatId> {

  private final GroupChatId id;
  private final boolean deleted;
  private final GroupChatName name;

  private final Members members;

  private final Messages messages;

  private final long sequenceNumber;

  private final long version;

  private GroupChat(
      GroupChatId id,
      boolean deleted,
      GroupChatName name,
      Members members,
      Messages messages,
      long sequenceNumber,
      long version) {
    this.id = id;
    this.deleted = deleted;
    this.name = name;
    this.members = members;
    this.messages = messages;
    this.sequenceNumber = sequenceNumber;
    this.version = version;
  }

  @Nonnull
  @Override
  public GroupChatId getId() {
    return id;
  }

  @Override
  public long getSequenceNumber() {
    return this.sequenceNumber;
  }

  @Override
  public long getVersion() {
    return this.version;
  }

  @Override
  public GroupChat withVersion(long l) {
    return new GroupChat(id, deleted, name, members, messages, sequenceNumber, l);
  }

  @Nonnull
  public GroupChatName getName() {
    return name;
  }

  public static Tuple2<GroupChat, GroupChatEvent> create(
      GroupChatId id, GroupChatName name, UserAccountId executorId) {
    long sequenceNumber = 1;
    long version = 1;
    return new Tuple2<>(
        new GroupChat(
            id,
            false,
            name,
            Members.ofAdministratorId(executorId),
            Messages.ofEmpty(),
            sequenceNumber,
            version),
        new GroupChatEvent.Created(
            UlidCreator.getMonotonicUlid(),
            id,
            executorId,
            name,
            Members.ofAdministratorId(executorId),
            sequenceNumber,
            Instant.now()));
  }

  public static GroupChat from(
      GroupChatId id,
      boolean deleted,
      GroupChatName name,
      Members members,
      Messages messages,
      long sequenceNumber,
      long version) {
    return new GroupChat(id, deleted, name, members, messages, sequenceNumber, version);
  }
}
