package com.github.j5ik2o.cqrs.es.java.command.processor;

import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.GroupChatRepository;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.*;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import io.vavr.Tuple2;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("write")
public class GroupChatCommandProcessor {

  private final GroupChatRepository groupChatRepository;

  @Autowired
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
            groupChatOpt -> {
              if (groupChatOpt.isEmpty()) {
                return CompletableFuture.failedFuture(new NotFoundProcessException("not found"));
              } else {
                var groupChat = groupChatOpt.get();
                var groupChatResult = groupChat.rename(name, modifierId);
                return groupChatResult.fold(
                    CompletableFuture::failedFuture,
                    groupChatRenamed ->
                        groupChatRepository
                            .store(groupChatRenamed._2, groupChatRenamed._1)
                            .thenApply(ignore -> groupChatRenamed._1.getId()));
              }
            });
  }

  public CompletableFuture<GroupChatId> deleteGroupChat(GroupChatId id, UserAccountId deleterId) {
    return groupChatRepository
        .findById(id)
        .thenCompose(
            groupChatOpt -> {
              if (groupChatOpt.isEmpty()) {
                return CompletableFuture.failedFuture(new NotFoundProcessException("not found"));
              } else {
                var groupChat = groupChatOpt.get();
                var groupChatResult = groupChat.delete(deleterId);
                return groupChatResult.fold(
                    CompletableFuture::failedFuture,
                    groupChatDeleted ->
                        groupChatRepository
                            .store(groupChatDeleted._2, groupChatDeleted._1)
                            .thenApply(ignore -> groupChatDeleted._1.getId()));
              }
            });
  }

  public CompletableFuture<GroupChatId> addMember(
      GroupChatId id, Member member, UserAccountId executorId) {
    return groupChatRepository
        .findById(id)
        .thenCompose(
            groupChatOpt -> {
              if (groupChatOpt.isEmpty()) {
                return CompletableFuture.failedFuture(new NotFoundProcessException("not found"));
              } else {
                var groupChat = groupChatOpt.get();
                var groupChatResult = groupChat.addMember(member, executorId);
                return groupChatResult.fold(
                    CompletableFuture::failedFuture,
                    groupChatMemberAdded ->
                        groupChatRepository
                            .store(groupChatMemberAdded._2, groupChatMemberAdded._1)
                            .thenApply(ignore -> groupChatMemberAdded._1.getId()));
              }
            });
  }

  public CompletableFuture<GroupChatId> removeMember(
      GroupChatId id, UserAccountId memberId, UserAccountId executorId) {
    return groupChatRepository
        .findById(id)
        .thenCompose(
            groupChatOpt -> {
              if (groupChatOpt.isEmpty()) {
                return CompletableFuture.failedFuture(new NotFoundProcessException("not found"));
              } else {
                var groupChat = groupChatOpt.get();
                var groupChatResult = groupChat.removeMemberByUserAccountId(memberId, executorId);
                return groupChatResult.fold(
                    CompletableFuture::failedFuture,
                    groupChatMemberRemoved ->
                        groupChatRepository
                            .store(groupChatMemberRemoved._2, groupChatMemberRemoved._1)
                            .thenApply(ignore -> groupChatMemberRemoved._1.getId()));
              }
            });
  }

  public CompletableFuture<Tuple2<GroupChatId, MessageId>> postMessage(
      GroupChatId id, Message message, UserAccountId executorId) {
    return groupChatRepository
        .findById(id)
        .thenCompose(
            groupChatOpt -> {
              if (groupChatOpt.isEmpty()) {
                return CompletableFuture.failedFuture(new NotFoundProcessException("not found"));
              } else {
                var groupChat = groupChatOpt.get();
                var groupChatResult = groupChat.postMessage(message, executorId);
                return groupChatResult.fold(
                    CompletableFuture::failedFuture,
                    groupChatMessagePosted ->
                        groupChatRepository
                            .store(groupChatMessagePosted._2, groupChatMessagePosted._1)
                            .thenApply(
                                ignore ->
                                    new Tuple2<>(
                                        groupChatMessagePosted._1.getId(), message.getId())));
              }
            });
  }

  public CompletableFuture<GroupChatId> deleteMessage(
      GroupChatId id, MessageId messageId, UserAccountId executorId) {
    return groupChatRepository
        .findById(id)
        .thenCompose(
            groupChatOpt -> {
              if (groupChatOpt.isEmpty()) {
                return CompletableFuture.failedFuture(new NotFoundProcessException("not found"));
              } else {
                var groupChat = groupChatOpt.get();
                var groupChatResult = groupChat.deleteMessageByMessageId(messageId, executorId);
                return groupChatResult.fold(
                    CompletableFuture::failedFuture,
                    groupChatMessageDeleted ->
                        groupChatRepository
                            .store(groupChatMessageDeleted._2, groupChatMessageDeleted._1)
                            .thenApply(ignore -> groupChatMessageDeleted._1.getId()));
              }
            });
  }
}
