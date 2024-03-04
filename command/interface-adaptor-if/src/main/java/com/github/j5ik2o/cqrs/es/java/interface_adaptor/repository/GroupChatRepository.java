package com.github.j5ik2o.cqrs.es.java.interface_adaptor.repository;

import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
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
