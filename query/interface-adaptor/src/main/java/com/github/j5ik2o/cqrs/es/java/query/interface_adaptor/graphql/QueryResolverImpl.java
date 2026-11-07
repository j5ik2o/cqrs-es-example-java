package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.graphql;

import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.GroupChatReadMapper;
import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.MemberReadMapper;
import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.MessageReadMapper;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
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
  public Mono<GroupChat> getGroupChat(
      @Argument("groupChatId") String groupChatId,
      @Argument("userAccountId") String userAccountId) {
    var record =
        groupChatReadMapper
            .getGroupChat(groupChatId, userAccountId)
            .orElseThrow(() -> new ReadModelNotFoundException("GroupChat"));
    return Mono.just(
        new GroupChat(
            record.id(), record.name(), record.ownerId(), record.createdAt(), record.updatedAt()));
  }

  @QueryMapping
  @Override
  public Flux<GroupChat> getGroupChats(@Argument("userAccountId") String userAccountId) {
    return Flux.fromIterable(groupChatReadMapper.getGroupChats(userAccountId))
        .map(
            record ->
                new GroupChat(
                    record.id(),
                    record.name(),
                    record.ownerId(),
                    record.createdAt(),
                    record.updatedAt()));
  }

  @QueryMapping
  @Override
  public Mono<Member> getMember(
      @Argument("groupChatId") String groupChatId,
      @Argument("userAccountId") String userAccountId) {
    var record =
        memberReadMapper
            .getMember(groupChatId, userAccountId)
            .orElseThrow(() -> new ReadModelNotFoundException("Member"));
    return Mono.just(
        new Member(
            record.id(),
            record.groupChatId(),
            record.userAccountId(),
            record.role(),
            record.createdAt(),
            record.updatedAt()));
  }

  @QueryMapping
  @Override
  public Flux<Member> getMembers(
      @Argument("groupChatId") String groupChatId,
      @Argument("userAccountId") String userAccountId) {
    return Flux.fromIterable(memberReadMapper.getMembers(groupChatId, userAccountId))
        .map(
            record ->
                new Member(
                    record.id(),
                    record.groupChatId(),
                    record.userAccountId(),
                    record.role(),
                    record.createdAt(),
                    record.updatedAt()));
  }

  @QueryMapping
  @Override
  public Mono<Message> getMessage(
      @Argument("messageId") String messageId, @Argument("userAccountId") String userAccountId) {
    var record =
        messageReadMapper
            .getMessage(messageId, userAccountId)
            .orElseThrow(() -> new ReadModelNotFoundException("Message"));
    return Mono.just(
        new Message(
            record.id(),
            record.groupChatId(),
            record.userAccountId(),
            record.text(),
            record.createdAt(),
            record.updatedAt()));
  }

  @QueryMapping
  @Override
  public Flux<Message> getMessages(
      @Argument("groupChatId") String groupChatId,
      @Argument("userAccountId") String userAccountId) {
    return Flux.fromIterable(messageReadMapper.getMessages(groupChatId, userAccountId))
        .map(
            record ->
                new Message(
                    record.id(),
                    record.groupChatId(),
                    record.userAccountId(),
                    record.text(),
                    record.createdAt(),
                    record.updatedAt()));
  }

  @GraphQlExceptionHandler
  public GraphQLError handleReadModelNotFound(
      ReadModelNotFoundException exception, DataFetchingEnvironment environment) {
    return GraphqlErrorBuilder.newError(environment)
        .message(exception.getMessage())
        .extensions(Map.of("code", "404"))
        .build();
  }
}
