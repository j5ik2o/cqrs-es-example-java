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
  public Mono<GroupChat> getGroupChat(String groupChatId, String userAccountId) throws Exception {
    return null;
  }

  @Override
  public Flux<GroupChat> getGroupChats(String userAccountId) throws Exception {
    return null;
  }

  @Override
  public Mono<Member> getMember(String groupChatId, String userAccountId) throws Exception {
    return null;
  }

  @Override
  public Flux<Member> getMembers(String groupChatId, String userAccountId) throws Exception {
    return null;
  }

  @Override
  public Mono<Message> getMessage(String messageId, String userAccountId) throws Exception {
    return null;
  }

  @Override
  public Flux<Message> getMessages(String groupChatId, String userAccountId) throws Exception {
    return null;
  }
}
