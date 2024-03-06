package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.graphql;

import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.GroupChatMapper;
import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.MemberMapper;
import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.MessageMapper;
import org.apache.ibatis.annotations.Arg;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
@Profile("read")
public class QueryResolverImpl implements QueryResolver {

  private final GroupChatMapper groupChatMapper;
  private final MemberMapper memberMapper;

  private final MessageMapper messageMapper;

  public QueryResolverImpl(
      GroupChatMapper groupChatMapper, MemberMapper memberMapper, MessageMapper messageMapper) {
    this.groupChatMapper = groupChatMapper;
    this.memberMapper = memberMapper;
    this.messageMapper = messageMapper;
  }

  @QueryMapping
  @Override
  public Mono<GroupChatOutput> getGroupChat(@Argument("groupChatId") String groupChatId, @Argument("userAccountId") String userAccountId)
      throws Exception {
    return Mono.justOrEmpty(
        groupChatMapper
            .getGroupChat(groupChatId, userAccountId)
            .map(
                groupChatRecord ->
                    new GroupChatOutput(
                        groupChatRecord.id(),
                        groupChatRecord.name(),
                        groupChatRecord.ownerId(),
                        groupChatRecord.createdAt().toString(),
                        groupChatRecord.updatedAt().toString())));
  }

  @QueryMapping
  @Override
  public Flux<GroupChatOutput> getGroupChats(@Argument("userAccountId") String userAccountId) throws Exception {
    return Flux.fromArray(
        groupChatMapper.getGroupChats(userAccountId).stream()
            .map(
                groupChatRecord ->
                    new GroupChatOutput(
                        groupChatRecord.id(),
                        groupChatRecord.name(),
                        groupChatRecord.ownerId(),
                        groupChatRecord.createdAt().toString(),
                        groupChatRecord.updatedAt().toString()))
            .toArray(GroupChatOutput[]::new));
  }

  @QueryMapping
  @Override
  public Mono<MemberOutput> getMember(@Argument("groupChatId") String groupChatId, @Argument("userAccountId") String userAccountId) throws Exception {
    return Mono.justOrEmpty(
        memberMapper
            .getMember(groupChatId, userAccountId)
            .map(
                memberRecord ->
                    new MemberOutput(
                        memberRecord.id(),
                        memberRecord.groupChatId(),
                        memberRecord.userAccountId(),
                        memberRecord.role(),
                        memberRecord.createdAt().toString(),
                        memberRecord.updatedAt().toString())));
  }

  @QueryMapping
  @Override
  public Flux<MemberOutput> getMembers(@Argument("groupChatId") String groupChatId, @Argument("userAccountId") String userAccountId) throws Exception {
    return Flux.just(
        memberMapper.getMembers(groupChatId, userAccountId).stream()
            .map(
                memberRecord ->
                    new MemberOutput(
                        memberRecord.id(),
                        memberRecord.groupChatId(),
                        memberRecord.userAccountId(),
                        memberRecord.role(),
                        memberRecord.createdAt().toString(),
                        memberRecord.updatedAt().toString()))
            .toArray(MemberOutput[]::new));
  }

  @QueryMapping
  @Override
  public Mono<MessageOutput> getMessage(@Argument("messageId") String messageId, @Argument("userAccountId") String userAccountId) throws Exception {
    return Mono.justOrEmpty(
        messageMapper
            .getMessage(messageId, userAccountId)
            .map(
                messageRecord ->
                    new MessageOutput(
                        messageRecord.id(),
                        messageRecord.groupChatId(),
                        messageRecord.senderId(),
                        messageRecord.content(),
                        messageRecord.createdAt().toString(),
                        messageRecord.updatedAt().toString())));
  }

  @QueryMapping
  @Override
  public Flux<MessageOutput> getMessages(@Argument("groupChatId") String groupChatId, @Argument("userAccountId") String userAccountId)
      throws Exception {
    return Flux.just(
        messageMapper.getMessages(groupChatId, userAccountId).stream()
            .map(
                messageRecord ->
                    new MessageOutput(
                        messageRecord.id(),
                        messageRecord.groupChatId(),
                        messageRecord.senderId(),
                        messageRecord.content(),
                        messageRecord.createdAt().toString(),
                        messageRecord.updatedAt().toString()))
            .toArray(MessageOutput[]::new));
  }
}
