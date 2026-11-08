package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.GroupChatReadMapper;
import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.MemberReadMapper;
import com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao.MessageReadMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.data.method.annotation.support.AnnotatedControllerConfigurer;
import org.springframework.graphql.execution.DefaultExecutionGraphQlService;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class QueryResolverTest {

  private static final String ADMIN_ID = "UserAccount-01H0000000000000000000001";
  private static final String MEMBER_ID = "UserAccount-01H0000000000000000000002";
  private static final String OUTSIDER_ID = "UserAccount-01H0000000000000000000003";
  private static final String GROUP_CHAT_ID = "GroupChat-01H0000000000000000000011";
  private static final String OTHER_GROUP_CHAT_ID = "GroupChat-01H0000000000000000000012";
  private static final String MESSAGE_ID = "Message-01H0000000000000000000021";
  private static final String OTHER_MESSAGE_ID = "Message-01H0000000000000000000022";

  private static final LocalDateTime GROUP_CHAT_CREATED_AT =
      LocalDateTime.of(2026, 11, 1, 10, 0, 0);
  private static final LocalDateTime GROUP_CHAT_UPDATED_AT =
      LocalDateTime.of(2026, 11, 1, 11, 30, 45);
  private static final LocalDateTime MEMBER_CREATED_AT = LocalDateTime.of(2026, 11, 1, 10, 1, 0);
  private static final LocalDateTime MESSAGE_CREATED_AT = LocalDateTime.of(2026, 11, 1, 10, 3, 0);

  @Container
  private static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:26.10"))
          .withConfigurationOverride("mysql-conf")
          .withDatabaseName("ceer")
          .withUsername("ceer")
          .withPassword("ceer");

  private static SqlSessionFactory sqlSessionFactory;

  private SqlSession sqlSession;
  private GraphQlTester graphQlTester;

  @BeforeAll
  static void setUpDatabase() {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .locations("filesystem:" + migrationsDirectory())
        .load()
        .migrate();

    var dataSource =
        new PooledDataSource(
            "com.mysql.cj.jdbc.Driver",
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword());
    var environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
    var configuration = new Configuration(environment);
    configuration.addMapper(GroupChatReadMapper.class);
    configuration.addMapper(MemberReadMapper.class);
    configuration.addMapper(MessageReadMapper.class);
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

    seedReadModel(new JdbcTemplate(dataSource));
  }

  @BeforeEach
  void setUpGraphQl() {
    sqlSession = sqlSessionFactory.openSession(true);
    var resolver =
        new QueryResolverImpl(
            sqlSession.getMapper(GroupChatReadMapper.class),
            sqlSession.getMapper(MemberReadMapper.class),
            sqlSession.getMapper(MessageReadMapper.class));

    var context = new StaticApplicationContext();
    context.getBeanFactory().registerSingleton("queryResolver", resolver);
    context.refresh();
    var configurer = new AnnotatedControllerConfigurer();
    configurer.setApplicationContext(context);
    configurer.afterPropertiesSet();

    var source =
        GraphQlSource.schemaResourceBuilder()
            .schemaResources(new ClassPathResource("graphql-read/schema.graphqls"))
            .configureRuntimeWiring(configurer)
            .configureRuntimeWiring(wiring -> wiring.scalar(NaiveDateTimeScalar.create()))
            .build();
    graphQlTester =
        ExecutionGraphQlServiceTester.create(new DefaultExecutionGraphQlService(source));
  }

  @AfterEach
  void tearDown() {
    sqlSession.close();
  }

  @Test
  void getGroupChatReturnsTheGroupChatToAMember() {
    var response =
        graphQlTester
            .document(
                """
                query GetGroupChat($groupChatId: String!, $userAccountId: String!) {
                  getGroupChat(groupChatId: $groupChatId, userAccountId: $userAccountId) {
                    id name ownerId createdAt updatedAt
                  }
                }
                """)
            .variable("groupChatId", GROUP_CHAT_ID)
            .variable("userAccountId", MEMBER_ID)
            .execute();

    response.path("getGroupChat.id").entity(String.class).isEqualTo(GROUP_CHAT_ID);
    response.path("getGroupChat.name").entity(String.class).isEqualTo("group-chat");
    response.path("getGroupChat.ownerId").entity(String.class).isEqualTo(ADMIN_ID);
    response.path("getGroupChat.createdAt").entity(String.class).isEqualTo("2026-11-01T10:00:00");
    response.path("getGroupChat.updatedAt").entity(String.class).isEqualTo("2026-11-01T11:30:45");
  }

  @Test
  void getGroupChatRejectsANonMember() {
    assertNotFound(
        """
        query GetGroupChat($groupChatId: String!, $userAccountId: String!) {
          getGroupChat(groupChatId: $groupChatId, userAccountId: $userAccountId) { id }
        }
        """,
        "groupChatId",
        GROUP_CHAT_ID,
        OUTSIDER_ID,
        "GroupChat not found.");
  }

  @Test
  void getGroupChatsOnlyListsChatsTheAccountBelongsTo() {
    var response =
        graphQlTester
            .document(
                """
                query GetGroupChats($userAccountId: String!) {
                  getGroupChats(userAccountId: $userAccountId) { id }
                }
                """)
            .variable("userAccountId", MEMBER_ID)
            .execute();

    response.path("getGroupChats[*].id").entityList(String.class).containsExactly(GROUP_CHAT_ID);
  }

  @Test
  void getGroupChatsReturnsTheOtherChatToItsMember() {
    graphQlTester
        .document(
            """
            query GetGroupChats($userAccountId: String!) {
              getGroupChats(userAccountId: $userAccountId) { id }
            }
            """)
        .variable("userAccountId", OUTSIDER_ID)
        .execute()
        .path("getGroupChats[*].id")
        .entityList(String.class)
        .containsExactly(OTHER_GROUP_CHAT_ID);
  }

  @Test
  void getMemberReturnsTheRequestedMember() {
    var response =
        graphQlTester
            .document(
                """
                query GetMember($groupChatId: String!, $userAccountId: String!) {
                  getMember(groupChatId: $groupChatId, userAccountId: $userAccountId) {
                    groupChatId userAccountId role createdAt updatedAt
                  }
                }
                """)
            .variable("groupChatId", GROUP_CHAT_ID)
            .variable("userAccountId", MEMBER_ID)
            .execute();

    response.path("getMember.groupChatId").entity(String.class).isEqualTo(GROUP_CHAT_ID);
    response.path("getMember.userAccountId").entity(String.class).isEqualTo(MEMBER_ID);
    response.path("getMember.role").entity(String.class).isEqualTo("MEMBER");
    response.path("getMember.createdAt").entity(String.class).isEqualTo("2026-11-01T10:01:00");
  }

  @Test
  void getMembersReturnsAllMembersToAMember() {
    var response =
        graphQlTester
            .document(
                """
                query GetMembers($groupChatId: String!, $userAccountId: String!) {
                  getMembers(groupChatId: $groupChatId, userAccountId: $userAccountId) {
                    userAccountId role
                  }
                }
                """)
            .variable("groupChatId", GROUP_CHAT_ID)
            .variable("userAccountId", MEMBER_ID)
            .execute();

    response
        .path("getMembers[*].userAccountId")
        .entityList(String.class)
        .containsExactly(ADMIN_ID, MEMBER_ID);
    response.path("getMembers[*].role").entityList(String.class).containsExactly("ADMIN", "MEMBER");
  }

  @Test
  void getMembersHidesMembersFromANonMember() {
    graphQlTester
        .document(
            """
            query GetMembers($groupChatId: String!, $userAccountId: String!) {
              getMembers(groupChatId: $groupChatId, userAccountId: $userAccountId) { userAccountId }
            }
            """)
        .variable("groupChatId", GROUP_CHAT_ID)
        .variable("userAccountId", OUTSIDER_ID)
        .execute()
        .path("getMembers[*].userAccountId")
        .entityList(String.class)
        .hasSize(0);
  }

  @Test
  void getMessageReturnsTheMessageToAMember() {
    var response =
        graphQlTester
            .document(
                """
                query GetMessage($messageId: String!, $userAccountId: String!) {
                  getMessage(messageId: $messageId, userAccountId: $userAccountId) {
                    id groupChatId userAccountId text createdAt updatedAt
                  }
                }
                """)
            .variable("messageId", MESSAGE_ID)
            .variable("userAccountId", MEMBER_ID)
            .execute();

    response.path("getMessage.id").entity(String.class).isEqualTo(MESSAGE_ID);
    response.path("getMessage.groupChatId").entity(String.class).isEqualTo(GROUP_CHAT_ID);
    response.path("getMessage.userAccountId").entity(String.class).isEqualTo(MEMBER_ID);
    response.path("getMessage.text").entity(String.class).isEqualTo("hello");
    response.path("getMessage.createdAt").entity(String.class).isEqualTo("2026-11-01T10:03:00");
  }

  @Test
  void getMessageHidesTheMessageFromANonMember() {
    assertNotFound(
        """
        query GetMessage($messageId: String!, $userAccountId: String!) {
          getMessage(messageId: $messageId, userAccountId: $userAccountId) { id }
        }
        """,
        "messageId",
        MESSAGE_ID,
        OUTSIDER_ID,
        "Message not found.");
  }

  @Test
  void getMessageHidesMessagesOfAnotherChat() {
    assertNotFound(
        """
        query GetMessage($messageId: String!, $userAccountId: String!) {
          getMessage(messageId: $messageId, userAccountId: $userAccountId) { id }
        }
        """,
        "messageId",
        OTHER_MESSAGE_ID,
        MEMBER_ID,
        "Message not found.");
  }

  @Test
  void getMessagesReturnsTheChatMessagesToAMember() {
    graphQlTester
        .document(
            """
            query GetMessages($groupChatId: String!, $userAccountId: String!) {
              getMessages(groupChatId: $groupChatId, userAccountId: $userAccountId) { id text }
            }
            """)
        .variable("groupChatId", GROUP_CHAT_ID)
        .variable("userAccountId", MEMBER_ID)
        .execute()
        .path("getMessages[*].id")
        .entityList(String.class)
        .containsExactly(MESSAGE_ID);
  }

  @Test
  void getMessagesHidesMessagesFromANonMember() {
    graphQlTester
        .document(
            """
            query GetMessages($groupChatId: String!, $userAccountId: String!) {
              getMessages(groupChatId: $groupChatId, userAccountId: $userAccountId) { id }
            }
            """)
        .variable("groupChatId", GROUP_CHAT_ID)
        .variable("userAccountId", OUTSIDER_ID)
        .execute()
        .path("getMessages[*].id")
        .entityList(String.class)
        .hasSize(0);
  }

  private void assertNotFound(
      String document, String variableName, String value, String userAccountId, String message) {
    graphQlTester
        .document(document)
        .variable(variableName, value)
        .variable("userAccountId", userAccountId)
        .execute()
        .errors()
        .satisfy(
            errors -> {
              assertEquals(
                  1,
                  errors.stream()
                      .filter(error -> "404".equals(error.getExtensions().get("code")))
                      .count(),
                  "expected exactly one 404 error, but was " + errors);
              assertTrue(
                  errors.stream().anyMatch(error -> message.equals(error.getMessage())),
                  "expected message '" + message + "', but was " + errors);
            });
  }

  private static void seedReadModel(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.update(
        "INSERT INTO group_chats (id, disabled, name, owner_id, created_at, updated_at)"
            + " VALUES (?, 0, ?, ?, ?, ?)",
        GROUP_CHAT_ID,
        "group-chat",
        ADMIN_ID,
        GROUP_CHAT_CREATED_AT,
        GROUP_CHAT_UPDATED_AT);
    jdbcTemplate.update(
        "INSERT INTO group_chats (id, disabled, name, owner_id, created_at, updated_at)"
            + " VALUES (?, 0, ?, ?, ?, ?)",
        OTHER_GROUP_CHAT_ID,
        "other-group-chat",
        OUTSIDER_ID,
        GROUP_CHAT_CREATED_AT,
        GROUP_CHAT_CREATED_AT);

    insertMember(
        jdbcTemplate, "Member-01H0000000000000000000031", GROUP_CHAT_ID, ADMIN_ID, "ADMIN");
    insertMember(
        jdbcTemplate, "Member-01H0000000000000000000032", GROUP_CHAT_ID, MEMBER_ID, "MEMBER");
    insertMember(
        jdbcTemplate,
        "Member-01H0000000000000000000033",
        OTHER_GROUP_CHAT_ID,
        OUTSIDER_ID,
        "ADMIN");

    insertMessage(jdbcTemplate, MESSAGE_ID, GROUP_CHAT_ID, MEMBER_ID, "hello");
    insertMessage(jdbcTemplate, OTHER_MESSAGE_ID, OTHER_GROUP_CHAT_ID, OUTSIDER_ID, "secret");
  }

  private static void insertMember(
      JdbcTemplate jdbcTemplate, String id, String groupChatId, String userAccountId, String role) {
    jdbcTemplate.update(
        "INSERT INTO members (id, group_chat_id, user_account_id, role, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        id,
        groupChatId,
        userAccountId,
        role,
        MEMBER_CREATED_AT,
        MEMBER_CREATED_AT);
  }

  private static void insertMessage(
      JdbcTemplate jdbcTemplate, String id, String groupChatId, String userAccountId, String text) {
    jdbcTemplate.update(
        "INSERT INTO messages (id, disabled, group_chat_id, user_account_id, text, created_at,"
            + " updated_at) VALUES (?, 0, ?, ?, ?, ?, ?)",
        id,
        groupChatId,
        userAccountId,
        text,
        MESSAGE_CREATED_AT,
        MESSAGE_CREATED_AT);
  }

  private static String migrationsDirectory() {
    var directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (directory != null) {
      var candidate = directory.resolve("tools/refinery/migrations");
      if (Files.isDirectory(candidate)) {
        return candidate.toString();
      }
      directory = directory.getParent();
    }
    throw new IllegalStateException("tools/refinery/migrations was not found");
  }
}
