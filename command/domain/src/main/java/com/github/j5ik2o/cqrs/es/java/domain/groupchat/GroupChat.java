package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.ulid.UlidCreator;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import com.github.j5ik2o.event.store.adapter.java.Aggregate;
import io.vavr.Tuple2;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import java.time.Instant;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class GroupChat implements Aggregate<GroupChat, GroupChatId> {

  private final GroupChatId id;
  private final boolean deleted;
  private final GroupChatName name;

  @JsonProperty("members")
  private final Members members;

  @JsonProperty("messages")
  private final Messages messages;

  private final long sequenceNumber;

  private final long version;

  private GroupChat(
      @Nonnull @JsonProperty("id") GroupChatId id,
      @JsonProperty("deleted") boolean deleted,
      @Nonnull @JsonProperty("name") GroupChatName name,
      @Nonnull @JsonProperty("members") Members members,
      @Nonnull @JsonProperty("messages") Messages messages,
      @JsonProperty("sequenceNumber") long sequenceNumber,
      @JsonProperty("version") long version) {
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

  public boolean isDeleted() {
    return deleted;
  }

  public Members getMembers() {
    return members;
  }

  public Messages getMessages() {
    return messages;
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

  public Either<GroupChatError, Tuple2<GroupChat, GroupChatEvent>> delete(
      UserAccountId executorId) {
    if (deleted) {
      return Either.left(new GroupChatError.AlreadyDeletedError(id));
    }
    if (!members.isAdministrator(executorId)) {
      return Either.left(new GroupChatError.NotAdministratorError(id, executorId));
    }
    var newSequenceNumber = this.sequenceNumber + 1;
    var newGroupChat = new GroupChat(id, true, name, members, messages, newSequenceNumber, version);
    return Either.right(
        new Tuple2<>(
            newGroupChat,
            new GroupChatEvent.Deleted(
                UlidCreator.getMonotonicUlid(), id, executorId, newSequenceNumber, Instant.now())));
  }

  public Either<GroupChatError, Tuple2<GroupChat, GroupChatEvent>> rename(
      GroupChatName name, UserAccountId executorId) {
    if (deleted) {
      return Either.left(new GroupChatError.AlreadyDeletedError(id));
    }
    if (!members.isMember(executorId)) {
      return Either.left(new GroupChatError.NotMemberError(id, executorId));
    }
    if (this.name.equals(name)) {
      return Either.left(new GroupChatError.AlreadyRenamedError(id, name));
    }
    var newSequenceNumber = this.sequenceNumber + 1;
    var newGroupChat =
        new GroupChat(id, deleted, name, members, messages, newSequenceNumber, version);
    return Either.right(
        new Tuple2<>(
            newGroupChat,
            new GroupChatEvent.Renamed(
                UlidCreator.getMonotonicUlid(),
                id,
                executorId,
                name,
                newSequenceNumber,
                Instant.now())));
  }

  public Either<GroupChatError, Tuple2<GroupChat, GroupChatEvent>> addMember(
      Member member, UserAccountId executorId) {
    if (deleted) {
      return Either.left(new GroupChatError.AlreadyDeletedError(id));
    }
    if (!members.isAdministrator(executorId)) {
      return Either.left(new GroupChatError.NotAdministratorError(id, executorId));
    }
    if (members.isMember(member.getUserAccountId())) {
      return Either.left(new GroupChatError.AlreadyMemberError(id, member.getUserAccountId()));
    }
    var newMembers = members.add(member);
    var newSequenceNumber = this.sequenceNumber + 1;
    var newGroupChat =
        new GroupChat(id, deleted, name, newMembers, messages, newSequenceNumber, version);
    var event =
        new GroupChatEvent.MemberAdded(
            UlidCreator.getMonotonicUlid(),
            id,
            member,
            executorId,
            newSequenceNumber,
            Instant.now());
    return Either.right(new Tuple2<>(newGroupChat, event));
  }

  public Either<GroupChatError, Tuple2<GroupChat, GroupChatEvent>> removeMemberByUserAccountId(
      UserAccountId userAccountId, UserAccountId executorId) {
    if (deleted) {
      return Either.left(new GroupChatError.AlreadyDeletedError(id));
    }
    if (!members.isAdministrator(executorId)) {
      return Either.left(new GroupChatError.NotAdministratorError(id, executorId));
    }
    if (!members.isMember(userAccountId)) {
      return Either.left(new GroupChatError.NotMemberError(id, userAccountId));
    }
    var memberAndMembersOption = members.removeByUserAccountId(userAccountId);
    if (memberAndMembersOption.isEmpty()) {
      return Either.left(new GroupChatError.NotMemberError(id, userAccountId));
    }
    var memberAndMembers = memberAndMembersOption.get();
    var newSequenceNumber = this.sequenceNumber + 1;
    var newGroupChat =
        new GroupChat(
            id, deleted, name, memberAndMembers._2(), messages, newSequenceNumber, version);
    var event =
        new GroupChatEvent.MemberRemoved(
            UlidCreator.getMonotonicUlid(),
            id,
            memberAndMembers._1(),
            executorId,
            newSequenceNumber,
            Instant.now());
    return Either.right(new Tuple2<>(newGroupChat, event));
  }

  public Either<GroupChatError, Tuple2<GroupChat, GroupChatEvent>> postMessage(
      Message message, UserAccountId executorId) {
    if (deleted) {
      return Either.left(new GroupChatError.AlreadyDeletedError(id));
    }
    if (!members.isMember(executorId)) {
      return Either.left(new GroupChatError.NotMemberError(id, executorId));
    }
    var newMessages = messages.add(message);
    var newSequenceNumber = this.sequenceNumber + 1;
    var newGroupChat =
        new GroupChat(id, deleted, name, members, newMessages, newSequenceNumber, version);
    var event =
        new GroupChatEvent.MessagePosted(
            UlidCreator.getMonotonicUlid(),
            id,
            message,
            executorId,
            newSequenceNumber,
            Instant.now());
    return Either.right(new Tuple2<>(newGroupChat, event));
  }

  public Either<GroupChatError, Tuple2<GroupChat, GroupChatEvent>> deleteMessageByMessageId(
      MessageId messageId, UserAccountId executorId) {
    if (deleted) {
      return Either.left(new GroupChatError.AlreadyDeletedError(id));
    }
    if (!members.isMember(executorId)) {
      return Either.left(new GroupChatError.NotAdministratorError(id, executorId));
    }
    if (!messages.containsByMessageId(messageId)) {
      return Either.left(new GroupChatError.MessageNotFoundError(id, messageId));
    }
    var messageAndMessagesOption = messages.removeByMessageId(messageId);
    if (messageAndMessagesOption.isEmpty()) {
      return Either.left(new GroupChatError.MessageNotFoundError(id, messageId));
    }
    var messageAndMessages = messageAndMessagesOption.get();
    var newSequenceNumber = this.sequenceNumber + 1;
    var newGroupChat =
        new GroupChat(
            id, deleted, name, members, messageAndMessages._2(), newSequenceNumber, version);
    var event =
        new GroupChatEvent.MessageDeleted(
            UlidCreator.getMonotonicUlid(),
            id,
            messageAndMessages._1(),
            executorId,
            newSequenceNumber,
            Instant.now());
    return Either.right(new Tuple2<>(newGroupChat, event));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GroupChat groupChat = (GroupChat) o;
    return deleted == groupChat.deleted
        && sequenceNumber == groupChat.sequenceNumber
        && version == groupChat.version
        && Objects.equals(id, groupChat.id)
        && Objects.equals(name, groupChat.name)
        && Objects.equals(members, groupChat.members)
        && Objects.equals(messages, groupChat.messages);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, deleted, name, members, messages, sequenceNumber, version);
  }

  @Override
  public String toString() {
    return "GroupChat{"
        + "id="
        + id
        + ", deleted="
        + deleted
        + ", name="
        + name
        + ", members="
        + members
        + ", messages="
        + messages
        + ", sequenceNumber="
        + sequenceNumber
        + ", version="
        + version
        + '}';
  }

  public GroupChat applyEvent(@Nonnull GroupChatEvent event) {
    if (event instanceof GroupChatEvent.Deleted) {
      var e = (GroupChatEvent.Deleted) event;
      return delete(e.executorId()).get()._1();
    } else if (event instanceof GroupChatEvent.Renamed) {
      var e = (GroupChatEvent.Renamed) event;
      return rename(e.name(), e.executorId()).get()._1();
    } else if (event instanceof GroupChatEvent.MemberAdded) {
      var e = (GroupChatEvent.MemberAdded) event;
      return addMember(e.member(), e.executorId()).get()._1();
    } else if (event instanceof GroupChatEvent.MemberRemoved) {
      var e = (GroupChatEvent.MemberRemoved) event;
      return removeMemberByUserAccountId(e.member().getUserAccountId(), e.executorId()).get()._1();
    } else if (event instanceof GroupChatEvent.MessagePosted) {
      var e = (GroupChatEvent.MessagePosted) event;
      return postMessage(e.message(), e.executorId()).get()._1();
    } else if (event instanceof GroupChatEvent.MessageDeleted) {
      var e = (GroupChatEvent.MessageDeleted) event;
      return deleteMessageByMessageId(e.message().getId(), e.executorId()).get()._1();
    } else {
      throw new IllegalArgumentException();
    }
  }

  public static GroupChat replay(Vector<GroupChatEvent> events, GroupChat snapshot) {
    return events.foldLeft(snapshot, GroupChat::applyEvent);
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
