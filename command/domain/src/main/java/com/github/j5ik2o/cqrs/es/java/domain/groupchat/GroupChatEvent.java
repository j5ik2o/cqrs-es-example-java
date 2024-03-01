package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import com.github.j5ik2o.event.store.adapter.java.Event;
import java.time.Instant;
import javax.annotation.Nonnull;

public sealed interface GroupChatEvent extends Event<GroupChatId> permits GroupChatEvent.Created {

  UserAccountId getExecutorId();

  record Created(
      String id,
      GroupChatId aggregateId,
      UserAccountId executorId,
      GroupChatName name,
      Members members,
      long sequenceNumber,
      Instant occurredAt)
      implements GroupChatEvent {
    @Override
    public UserAccountId getExecutorId() {
      return executorId;
    }

    @Nonnull
    @Override
    public String getId() {
      return id();
    }

    @Nonnull
    @Override
    public GroupChatId getAggregateId() {
      return aggregateId();
    }

    @Override
    public long getSequenceNumber() {
      return sequenceNumber();
    }

    @Nonnull
    @Override
    public Instant getOccurredAt() {
      return occurredAt();
    }

    @Override
    public boolean isCreated() {
      return true;
    }
  }
}
