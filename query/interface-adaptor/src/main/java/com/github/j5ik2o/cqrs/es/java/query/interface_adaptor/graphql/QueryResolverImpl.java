package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.graphql;

import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.GroupChatReadMapper;
import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.MemberReadMapper;
import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.MessageReadMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
@Profile("read")
public class QueryResolverImpl implements QueryResolver {

  private final GroupChatReadMapper groupChatReadMapper;
  private final MemberReadMapper memberReadMapper;

  private final MessageReadMapper messageReadMapper;

  public QueryResolverImpl(
      GroupChatReadMapper groupChatReadMapper,
      MemberReadMapper memberReadMapper,
      MessageReadMapper messageReadMapper) {
    this.groupChatReadMapper = groupChatReadMapper;
    this.memberReadMapper = memberReadMapper;
    this.messageReadMapper = messageReadMapper;
  }

  @QueryMapping
  @Override
  public Mono<GroupChatOutput> getGroupChat(
      @Argument("groupChatId") String groupChatId, @Argument("userAccountId") String userAccountId)
      throws Exception {
    return Mono.justOrEmpty(
        groupChatReadMapper
            .getGroupChat(groupChatId, userAccountId)
            .map(
                groupChatRecord ->
                    new GroupChatOutput(
                        groupChatRecord.id(),
                        groupChatRecord.name(),
                        groupChatRecord.ownerId(),
                        groupChatRecord.createdAt(),
                        groupChatRecord.updatedAt())));
  }

  @QueryMapping
  @Override
  public Flux<GroupChatOutput> getGroupChats(@Argument("userAccountId") String userAccountId)
      throws Exception {
    return Flux.fromArray(
        groupChatReadMapper.getGroupChats(userAccountId).stream()
            .map(
                groupChatRecord ->
                    new GroupChatOutput(
                        groupChatRecord.id(),
                        groupChatRecord.name(),
                        groupChatRecord.ownerId(),
                        groupChatRecord.createdAt(),
                        groupChatRecord.updatedAt()))
            .toArray(GroupChatOutput[]::new));
  }

  @QueryMapping
  @Override
  public Mono<MemberOutput> getMember(
      @Argument("groupChatId") String groupChatId, @Argument("userAccountId") String userAccountId)
      throws Exception {
    return Mono.justOrEmpty(
        memberReadMapper
            .getMember(groupChatId, userAccountId)
            .map(
                memberRecord ->
                    new MemberOutput(
                        memberRecord.id(),
                        memberRecord.groupChatId(),
                        memberRecord.userAccountId(),
                        memberRecord.role(),
                        memberRecord.createdAt(),
                        memberRecord.updatedAt())));
  }

  @QueryMapping
  @Override
  public Flux<MemberOutput> getMembers(
      @Argument("groupChatId") String groupChatId, @Argument("userAccountId") String userAccountId)
      throws Exception {
    return Flux.just(
        memberReadMapper.getMembers(groupChatId, userAccountId).stream()
            .map(
                memberRecord ->
                    new MemberOutput(
                        memberRecord.id(),
                        memberRecord.groupChatId(),
                        memberRecord.userAccountId(),
                        memberRecord.role(),
                        memberRecord.createdAt(),
                        memberRecord.updatedAt()))
            .toArray(MemberOutput[]::new));
  }

  @QueryMapping
  @Override
  public Mono<MessageOutput> getMessage(
      @Argument("messageId") String messageId, @Argument("userAccountId") String userAccountId)
      throws Exception {
    return Mono.justOrEmpty(
        messageReadMapper
            .getMessage(messageId, userAccountId)
            .map(
                messageRecord ->
                    new MessageOutput(
                        messageRecord.id(),
                        messageRecord.groupChatId(),
                        messageRecord.senderId(),
                        messageRecord.content(),
                        messageRecord.createdAt(),
                        messageRecord.updatedAt())));
  }

  @QueryMapping
  @Override
  public Flux<MessageOutput> getMessages(
      @Argument("groupChatId") String groupChatId, @Argument("userAccountId") String userAccountId)
      throws Exception {
    return Flux.just(
        messageReadMapper.getMessages(groupChatId, userAccountId).stream()
            .map(
                messageRecord ->
                    new MessageOutput(
                        messageRecord.id(),
                        messageRecord.groupChatId(),
                        messageRecord.senderId(),
                        messageRecord.content(),
                        messageRecord.createdAt(),
                        messageRecord.updatedAt()))
            .toArray(MessageOutput[]::new));
  }
}
