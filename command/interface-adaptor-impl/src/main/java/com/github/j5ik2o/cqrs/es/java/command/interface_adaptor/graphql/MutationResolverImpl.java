package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.graphql;

import com.github.j5ik2o.cqrs.es.java.command.processor.GroupChatCommandProcessor;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.*;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
@Profile("write")
public class MutationResolverImpl implements MutationResolver {

  private final GroupChatCommandProcessor groupChatCommandProcessor;

  public MutationResolverImpl(GroupChatCommandProcessor groupChatCommandProcessor) {
    this.groupChatCommandProcessor = groupChatCommandProcessor;
  }

  @MutationMapping
  @Override
  public Mono<GroupChatOutput> createGroupChat(@Argument("input") CreateGroupChatInput input)
      throws Exception {
    Mono<GroupChatName> nameMono =
        GroupChatName.validate(input.getName()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> executorIdMono =
        UserAccountId.validate(input.getExecutorId()).fold(Mono::error, Mono::just);

    return Mono.zip(nameMono, executorIdMono, groupChatCommandProcessor::createGroupChat)
        .flatMap(Mono::fromFuture)
        .map(result -> new GroupChatOutput(result.asString()));
  }

  @MutationMapping
  @Override
  public Mono<GroupChatOutput> deleteGroupChat(@Argument("input") DeleteGroupChatInput input)
      throws Exception {
    Mono<GroupChatId> groupChatId =
        GroupChatId.validate(input.getGroupChatId()).fold(Mono::error, Mono::just);
    Mono<UserAccountId> executorId =
        UserAccountId.validate(input.getExecutorId()).fold(Mono::error, Mono::just);

    return Mono.zip(groupChatId, executorId, groupChatCommandProcessor::deleteGroupChat)
        .flatMap(Mono::fromFuture)
        .map(result -> new GroupChatOutput(result.asString()));
  }

  @MutationMapping
  @Override
  public Mono<GroupChatOutput> renameGroupChat(@Argument("input") RenameGroupChatInput input)
      throws Exception {
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

  @MutationMapping
  @Override
  public Mono<GroupChatOutput> addMember(@Argument("input") AddMemberInput input) throws Exception {
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

  @MutationMapping
  @Override
  public Mono<GroupChatOutput> removeMember(@Argument("input") RemoveMemberInput input)
      throws Exception {
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

  @MutationMapping
  @Override
  public Mono<MessageOutput> postMessage(@Argument("input") PostMessageInput input)
      throws Exception {
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

  @MutationMapping
  @Override
  public Mono<GroupChatOutput> deleteMessage(@Argument("input") DeleteMessageInput input)
      throws Exception {
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
