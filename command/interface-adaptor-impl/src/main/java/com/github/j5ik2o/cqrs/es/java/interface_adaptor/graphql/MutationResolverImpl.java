package com.github.j5ik2o.cqrs.es.java.interface_adaptor.graphql;

import com.github.j5ik2o.cqrs.es.java.domain.groupchat.*;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import com.github.j5ik2o.cqrs.es.java.processor.GroupChatCommandProcessor;
import java.time.LocalDateTime;
import reactor.core.publisher.Mono;

public class MutationResolverImpl implements MutationResolver {

  private final GroupChatCommandProcessor groupChatCommandProcessor;

  public MutationResolverImpl(GroupChatCommandProcessor groupChatCommandProcessor) {
    this.groupChatCommandProcessor = groupChatCommandProcessor;
  }

  @Override
  public Mono<GroupChatOutput> createGroupChat(CreateGroupChatInput input) throws Exception {
    Mono<GroupChatName> nameMono =
        GroupChatName.validate(input.getName()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> executorIdMono =
        UserAccountId.validate(input.getExecutorId()).fold(Mono::error, Mono::just);

    return Mono.zip(nameMono, executorIdMono, groupChatCommandProcessor::createGroupChat)
        .flatMap(Mono::fromFuture)
        .map(result -> new GroupChatOutput(result.asString()));
  }

  @Override
  public Mono<GroupChatOutput> deleteGroupChat(DeleteGroupChatInput input) throws Exception {
    Mono<GroupChatId> groupChatId =
        GroupChatId.validate(input.getGroupChatId()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> executorId =
        UserAccountId.validate(input.getExecutorId()).fold(Mono::error, Mono::just);

    return Mono.zip(groupChatId, executorId, groupChatCommandProcessor::deleteGroupChat)
        .flatMap(Mono::fromFuture)
        .map(result -> new GroupChatOutput(result.asString()));
  }

  @Override
  public Mono<GroupChatOutput> renameGroupChat(RenameGroupChatInput input) throws Exception {
    Mono<GroupChatId> groupChatIdMono =
        GroupChatId.validate(input.getGroupChatId()).fold(Mono::error, Mono::just);
    Mono<GroupChatName> nameMono =
        GroupChatName.validate(input.getName()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> executorIdMono =
        UserAccountId.validate(input.getExecutorId()).fold(Mono::error, Mono::just);

    return Mono.zip(groupChatIdMono, nameMono, executorIdMono)
        .flatMap(
            tuple ->
                Mono.fromFuture(
                    groupChatCommandProcessor.renameGroupChat(
                        tuple.getT1(), tuple.getT2(), tuple.getT3())))
        .map(result -> new GroupChatOutput(result.asString()));
  }

  @Override
  public Mono<GroupChatOutput> addMember(AddMemberInput input) throws Exception {
    Mono<GroupChatId> groupChatIdMono =
        GroupChatId.validate(input.getGroupChatId()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> userAccountIdMono =
        UserAccountId.validate(input.getUserAccountId()).fold(Mono::error, Mono::just);
    Mono<MemberRole> memberRoleMono =
        MemberRole.validate(input.getRole().name().toLowerCase()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> executorIdMono =
        UserAccountId.validate(input.getExecutorId()).fold(Mono::error, Mono::just);
    Mono<Member> memberMono =
        Mono.zip(
            userAccountIdMono,
            memberRoleMono,
            (userAccountId, memberRole) ->
                Member.of(MemberId.generate(), userAccountId, memberRole));

    return Mono.zip(groupChatIdMono, memberMono, executorIdMono)
        .flatMap(
            tuple ->
                Mono.fromFuture(
                    groupChatCommandProcessor.addMember(
                        tuple.getT1(), tuple.getT2(), tuple.getT3())))
        .map(result -> new GroupChatOutput(result.asString()));
  }

  @Override
  public Mono<GroupChatOutput> removeMember(RemoveMemberInput input) throws Exception {
    Mono<GroupChatId> groupChatIdMono =
        GroupChatId.validate(input.getGroupChatId()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> userAccountIdMono =
        UserAccountId.validate(input.getUserAccountId()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> executorIdMono =
        UserAccountId.validate(input.getExecutorId()).fold(Mono::error, Mono::just);

    return Mono.zip(groupChatIdMono, userAccountIdMono, executorIdMono)
        .flatMap(
            tuple ->
                Mono.fromFuture(
                    groupChatCommandProcessor.removeMember(
                        tuple.getT1(), tuple.getT2(), tuple.getT3())))
        .map(result -> new GroupChatOutput(result.asString()));
  }

  @Override
  public Mono<MessageOutput> postMessage(PostMessageInput input) throws Exception {
    Mono<GroupChatId> groupChatIdMono =
        GroupChatId.validate(input.getGroupChatId()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> executorIdMono =
        UserAccountId.validate(input.getExecutorId()).fold(Mono::error, Mono::just);
    Mono<MessageId> messageIdMono = Mono.just(MessageId.generate());
    Mono<Message> messageMono =
        Mono.zip(
                messageIdMono,
                Mono.just(input.getContent()),
                executorIdMono,
                Mono.just(LocalDateTime.now()))
            .map(tuple -> Message.of(tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()));

    return Mono.zip(groupChatIdMono, messageMono, executorIdMono)
        .flatMap(
            tuple ->
                Mono.fromFuture(
                    groupChatCommandProcessor.postMessage(
                        tuple.getT1(), tuple.getT2(), tuple.getT3())))
        .map(result -> new MessageOutput(result._1.asString(), result._2.asString()));
  }

  @Override
  public Mono<GroupChatOutput> deleteMessage(DeleteMessageInput input) throws Exception {
    Mono<GroupChatId> groupChatIdMono =
        GroupChatId.validate(input.getGroupChatId()).fold(Mono::error, Mono::just);
    Mono<MessageId> messageIdMono =
        MessageId.validate(input.getMessageId()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> executorIdMono =
        UserAccountId.validate(input.getExecutorId()).fold(Mono::error, Mono::just);

    return Mono.zip(groupChatIdMono, messageIdMono, executorIdMono)
        .flatMap(
            tuple ->
                Mono.fromFuture(
                    groupChatCommandProcessor.deleteMessage(
                        tuple.getT1(), tuple.getT2(), tuple.getT3())))
        .map(result -> new GroupChatOutput(result.asString()));
  }
}
