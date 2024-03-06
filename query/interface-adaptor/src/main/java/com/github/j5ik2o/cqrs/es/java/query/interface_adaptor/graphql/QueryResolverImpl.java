package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.graphql;

import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.GroupChatMapper;
import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.MemberMapper;
import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.MessageMapper;
import org.springframework.context.annotation.Profile;
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

  @Override
  public Mono<GroupChatOutput> getGroupChat(String groupChatId, String userAccountId)
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

  @Override
  public Flux<GroupChatOutput> getGroupChats(String userAccountId) throws Exception {
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

  @Override
  public Mono<MemberOutput> getMember(String groupChatId, String userAccountId) throws Exception {
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

  @Override
  public Flux<MemberOutput> getMembers(String groupChatId, String userAccountId) throws Exception {
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

  @Override
  public Mono<MessageOutput> getMessage(String messageId, String userAccountId) throws Exception {
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

  @Override
  public Flux<MessageOutput> getMessages(String groupChatId, String userAccountId)
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
