package com.github.j5ik2o.cqrs.es.java.processor;

import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatName;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatRepository;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import java.util.concurrent.CompletableFuture;

public class GroupChatCommandProcessor {

  private final GroupChatRepository groupChatRepository;

  public GroupChatCommandProcessor(GroupChatRepository groupChatRepository) {
    this.groupChatRepository = groupChatRepository;
  }

  public CompletableFuture<GroupChatId> createGroupChat(
      GroupChatName name, UserAccountId creatorId) {
    var groupChatTuple2 = GroupChat.create(GroupChatId.generate(), name, creatorId);
    return groupChatRepository
        .store(groupChatTuple2._2, groupChatTuple2._1)
        .thenApply(v -> groupChatTuple2._1.getId());
  }

  public CompletableFuture<GroupChatId> renameGroupChat(
      GroupChatId id, GroupChatName name, UserAccountId modifierId) {
    return groupChatRepository
        .findById(id)
        .thenCompose(
            opt -> {
              if (opt.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("not found"));
              } else {
                var groupChat = opt.get();
                var groupChatResult = groupChat.rename(name, modifierId);
                return groupChatResult.fold(
                    e -> CompletableFuture.failedFuture(new IllegalArgumentException(e)),
                    groupChatRenamed ->
                        groupChatRepository
                            .store(groupChatRenamed._2, groupChatRenamed._1)
                            .thenApply(v -> groupChatRenamed._1.getId()));
              }
            });
  }
}
