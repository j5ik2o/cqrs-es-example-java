package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.graphql;

import static org.junit.jupiter.api.Assertions.*;

import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.GroupChatRepository;
import com.github.j5ik2o.cqrs.es.java.command.processor.GroupChatCommandProcessor;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import io.vavr.control.Option;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.execution.DefaultExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;

class MutationResolverTest {

  private GraphQlTester graphQlTester;
  private String adminId;
  private String userAccountId;

  @BeforeEach
  void setUp() {
    var repository = new InMemoryGroupChatRepository();
    var processor = new GroupChatCommandProcessor(repository);
    var context = new StaticApplicationContext();
    context
        .getBeanFactory()
        .registerSingleton("mutationResolver", new MutationResolverImpl(processor));
    context.getBeanFactory().registerSingleton("queryResolver", new QueryResolverImpl());
    context.refresh();
    var configurer = new AnnotatedControllerConfigurer();
    configurer.setApplicationContext(context);
    configurer.afterPropertiesSet();

    var source =
        GraphQlSource.schemaResourceBuilder()
            .schemaResources(new ClassPathResource("graphql-write/schema.graphqls"))
            .configureRuntimeWiring(configurer)
            .build();
    graphQlTester =
        ExecutionGraphQlServiceTester.create(new DefaultExecutionGraphQlService(source));
    adminId = UserAccountId.generate().asString();
    userAccountId = UserAccountId.generate().asString();
  }

  @Test
  void healthCheckReturnsOk() {
    graphQlTester
        .document("{ healthCheck }")
        .execute()
        .path("healthCheck")
        .entity(String.class)
        .isEqualTo("OK");
  }

  @Test
  void allMutationsWorkThroughGraphQl() {
    var groupChatId =
        graphQlTester
            .document(
                """
                mutation CreateGroupChat($input: CreateGroupChatInput!) {
                  createGroupChat(input: $input) { groupChatId }
                }
                """)
            .variable("input", Map.of("name", "group-chat-example", "executorId", adminId))
            .execute()
            .path("createGroupChat.groupChatId")
            .entity(String.class)
            .get();

    assertEquals(groupChatId, addMember(groupChatId, "ADMIN"));
    var messageId = postMessage(groupChatId);
    assertEquals(groupChatId, editMessage(groupChatId, messageId));
    assertEquals(groupChatId, renameGroupChat(groupChatId));
    assertEquals(groupChatId, deleteMessage(groupChatId, messageId));
    assertEquals(groupChatId, removeMember(groupChatId));
    assertEquals(groupChatId, deleteGroupChat(groupChatId));
  }

  private String addMember(String groupChatId, String role) {
    return graphQlTester
        .document(
            """
            mutation AddMember($input: AddMemberInput!) {
              addMember(input: $input) { groupChatId }
            }
            """)
        .variable(
            "input",
            Map.of(
                "groupChatId", groupChatId,
                "userAccountId", userAccountId,
                "role", role,
                "executorId", adminId))
        .execute()
        .path("addMember.groupChatId")
        .entity(String.class)
        .get();
  }

  private String postMessage(String groupChatId) {
    return graphQlTester
        .document(
            """
            mutation PostMessage($input: PostMessageInput!) {
              postMessage(input: $input) { groupChatId messageId }
            }
            """)
        .variable(
            "input",
            Map.of(
                "groupChatId", groupChatId,
                "content", "Text1",
                "executorId", userAccountId))
        .execute()
        .path("postMessage.messageId")
        .entity(String.class)
        .get();
  }

  private String editMessage(String groupChatId, String messageId) {
    return graphQlTester
        .document(
            """
            mutation EditMessage($input: EditMessageInput!) {
              editMessage(input: $input) { groupChatId }
            }
            """)
        .variable(
            "input",
            Map.of(
                "groupChatId", groupChatId,
                "messageId", messageId,
                "content", "Text2",
                "executorId", userAccountId))
        .execute()
        .path("editMessage.groupChatId")
        .entity(String.class)
        .get();
  }

  private String renameGroupChat(String groupChatId) {
    return graphQlTester
        .document(
            """
            mutation RenameGroupChat($input: RenameGroupChatInput!) {
              renameGroupChat(input: $input) { groupChatId }
            }
            """)
        .variable(
            "input",
            Map.of(
                "groupChatId", groupChatId,
                "name", "group-chat-example-2",
                "executorId", adminId))
        .execute()
        .path("renameGroupChat.groupChatId")
        .entity(String.class)
        .get();
  }

  private String deleteMessage(String groupChatId, String messageId) {
    return graphQlTester
        .document(
            """
            mutation DeleteMessage($input: DeleteMessageInput!) {
              deleteMessage(input: $input) { groupChatId }
            }
            """)
        .variable(
            "input",
            Map.of(
                "groupChatId", groupChatId,
                "messageId", messageId,
                "executorId", userAccountId))
        .execute()
        .path("deleteMessage.groupChatId")
        .entity(String.class)
        .get();
  }

  private String removeMember(String groupChatId) {
    return graphQlTester
        .document(
            """
            mutation RemoveMember($input: RemoveMemberInput!) {
              removeMember(input: $input) { groupChatId }
            }
            """)
        .variable(
            "input",
            Map.of(
                "groupChatId", groupChatId,
                "userAccountId", userAccountId,
                "executorId", adminId))
        .execute()
        .path("removeMember.groupChatId")
        .entity(String.class)
        .get();
  }

  private String deleteGroupChat(String groupChatId) {
    return graphQlTester
        .document(
            """
            mutation DeleteGroupChat($input: DeleteGroupChatInput!) {
              deleteGroupChat(input: $input) { groupChatId }
            }
            """)
        .variable("input", Map.of("groupChatId", groupChatId, "executorId", adminId))
        .execute()
        .path("deleteGroupChat.groupChatId")
        .entity(String.class)
        .get();
  }

  private static final class InMemoryGroupChatRepository implements GroupChatRepository {
    private final Map<GroupChatId, GroupChat> store = new ConcurrentHashMap<>();

    @Override
    public GroupChatRepository withSnapshotPredicate(
        Option<BiPredicate<GroupChatEvent, GroupChat>> predicate) {
      return this;
    }

    @Override
    public GroupChatRepository withRetentionCriteriaOf(long numberOfEvents) {
      return this;
    }

    @Override
    public CompletableFuture<Option<GroupChat>> findById(GroupChatId id) {
      return CompletableFuture.completedFuture(Option.of(store.get(id)));
    }

    @Override
    public CompletableFuture<Void> store(GroupChatEvent event, GroupChat aggregate) {
      store.put(aggregate.getId(), aggregate);
      return CompletableFuture.completedFuture(null);
    }
  }
}
