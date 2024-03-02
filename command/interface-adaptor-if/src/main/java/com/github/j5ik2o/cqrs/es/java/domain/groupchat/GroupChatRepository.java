package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import io.vavr.control.Option;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

public interface GroupChatRepository {
  GroupChatRepository withSnapshotPredicate(
      Option<BiPredicate<GroupChatEvent, GroupChat>> predicate);

  GroupChatRepository withRetentionCriteriaOf(long numberOfEvents);

  CompletableFuture<Option<GroupChat>> findById(GroupChatId id);

  CompletableFuture<Void> store(GroupChatEvent event, GroupChat aggregate);
}
