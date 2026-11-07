package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization.GroupChatEventSerializer;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatName;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Member;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.MemberId;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.MemberRole;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Message;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.MessageId;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.GroupChatWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.MemberWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.MessageWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.ReadModelPositionMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ReadModelUpdaterTest {

  private static final Instant BASE_TIME = Instant.parse("2026-11-07T12:00:00Z");

  @Container
  private static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:26.10"))
          .withConfigurationOverride("mysql-conf")
          .withDatabaseName("ceer")
          .withUsername("ceer")
          .withPassword("ceer");

  private static SqlSessionFactory sqlSessionFactory;
  private static JdbcTemplate jdbcTemplate;

  private final GroupChatEventSerializer serializer = new GroupChatEventSerializer();

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
    configuration.addMapper(GroupChatWriteMapper.class);
    configuration.addMapper(MemberWriteMapper.class);
    configuration.addMapper(MessageWriteMapper.class);
    configuration.addMapper(ReadModelPositionMapper.class);
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @BeforeEach
  void clearReadModel() {
    jdbcTemplate.update("DELETE FROM messages");
    jdbcTemplate.update("DELETE FROM members");
    jdbcTemplate.update("DELETE FROM group_chats");
    jdbcTemplate.update("DELETE FROM read_model_positions");
  }

  @Test
  void appliesTheEventsOfOneHeadRecordInContiguousOrder() {
    var fixture = createFixture();
    var updater = new ReadModelUpdater(sqlSessionFactory, new InMemoryJournalReader());

    updater.update(
        headRecord(
            fixture.groupChatId().asString(),
            "MODIFY",
            List.of(
                headEvent(1, fixture.created()),
                headEvent(2, fixture.memberAdded()),
                headEvent(3, fixture.messagePosted()))));

    assertEquals("group-chat", groupChatName(fixture.groupChatId()));
    assertEquals(2, count("SELECT COUNT(*) FROM members WHERE group_chat_id = ?", fixture.aid()));
    assertEquals("hello", messageText(fixture.messageId()));
    assertEquals(3L, positionOf(fixture.aid()));
  }

  @Test
  void dropsDuplicateAndStaleSequenceNumbers() {
    var fixture = createFixture();
    var journal = new InMemoryJournalReader();
    var updater = new ReadModelUpdater(sqlSessionFactory, journal);
    var record =
        headRecord(
            fixture.groupChatId().asString(),
            "MODIFY",
            List.of(headEvent(1, fixture.created()), headEvent(2, fixture.memberAdded())));

    updater.update(record);
    updater.update(record);
    updater.update(
        headRecord(
            fixture.groupChatId().asString(),
            "MODIFY",
            List.of(headEvent(2, fixture.memberAdded()))));

    assertEquals(2L, positionOf(fixture.aid()));
    assertEquals(1, count("SELECT COUNT(*) FROM group_chats WHERE id = ?", fixture.aid()));
    assertEquals(2, count("SELECT COUNT(*) FROM members WHERE group_chat_id = ?", fixture.aid()));
  }

  @Test
  void sortsTheEventsOfAHeadRecordBySequenceNumber() {
    var fixture = createFixture();
    var updater = new ReadModelUpdater(sqlSessionFactory, new InMemoryJournalReader());

    updater.update(
        headRecord(
            fixture.groupChatId().asString(),
            "MODIFY",
            List.of(
                headEvent(3, fixture.messagePosted()),
                headEvent(1, fixture.created()),
                headEvent(2, fixture.memberAdded()))));

    assertEquals(3L, positionOf(fixture.aid()));
    assertEquals("hello", messageText(fixture.messageId()));
  }

  @Test
  void fillsASequenceGapFromTheJournal() {
    var fixture = createFixture();
    var journal = new InMemoryJournalReader();
    var updater = new ReadModelUpdater(sqlSessionFactory, journal);
    updater.update(
        headRecord(
            fixture.groupChatId().asString(), "INSERT", List.of(headEvent(1, fixture.created()))));

    journal.put(fixture.aid(), headEvent(2, fixture.memberAdded()));

    updater.update(
        headRecord(
            fixture.groupChatId().asString(),
            "MODIFY",
            List.of(headEvent(3, fixture.messagePosted()))));

    assertEquals(3L, positionOf(fixture.aid()));
    assertEquals(2, count("SELECT COUNT(*) FROM members WHERE group_chat_id = ?", fixture.aid()));
    assertEquals("hello", messageText(fixture.messageId()));
  }

  @Test
  void failsWhenTheJournalCannotFillTheGap() {
    var fixture = createFixture();
    var updater = new ReadModelUpdater(sqlSessionFactory, new InMemoryJournalReader());
    updater.update(
        headRecord(
            fixture.groupChatId().asString(), "INSERT", List.of(headEvent(1, fixture.created()))));

    assertThrows(
        ReadModelUpdateException.class,
        () ->
            updater.update(
                headRecord(
                    fixture.groupChatId().asString(),
                    "MODIFY",
                    List.of(headEvent(3, fixture.messagePosted())))));

    assertEquals(1L, positionOf(fixture.aid()));
    assertEquals(0, count("SELECT COUNT(*) FROM messages WHERE group_chat_id = ?", fixture.aid()));
  }

  @Test
  void commitsEarlierEventsAndRollsBackOnlyTheFailedEvent() {
    var fixture = createFixture();
    var updater = new ReadModelUpdater(sqlSessionFactory, new InMemoryJournalReader());
    updater.update(
        headRecord(
            fixture.groupChatId().asString(), "INSERT", List.of(headEvent(1, fixture.created()))));

    assertThrows(
        ReadModelUpdateException.class,
        () ->
            updater.update(
                headRecord(
                    fixture.groupChatId().asString(),
                    "MODIFY",
                    List.of(headEvent(2, fixture.messagePosted()), invalidHeadEvent(3)))));

    assertEquals(2L, positionOf(fixture.aid()));
    assertEquals(1, count("SELECT COUNT(*) FROM messages WHERE group_chat_id = ?", fixture.aid()));
    assertEquals("hello", messageText(fixture.messageId()));

    updater.update(
        headRecord(
            fixture.groupChatId().asString(),
            "MODIFY",
            List.of(headEvent(2, fixture.messagePosted()), headEvent(3, fixture.messageEdited()))));

    assertEquals(3L, positionOf(fixture.aid()));
    assertEquals("edited", messageText(fixture.messageId()));
  }

  @Test
  void rollsBackThePositionWhenTheReadModelWriteFails() {
    var fixture = createFixture();
    var updater = new ReadModelUpdater(sqlSessionFactory, new InMemoryJournalReader());

    assertThrows(
        RuntimeException.class,
        () ->
            updater.update(
                headRecord(
                    fixture.groupChatId().asString(),
                    "INSERT",
                    List.of(headEvent(1, fixture.messagePosted())))));

    assertNull(positionOf(fixture.aid()));
    assertEquals(0, count("SELECT COUNT(*) FROM messages WHERE group_chat_id = ?", fixture.aid()));
  }

  @Test
  void resumesFromThePersistedPositionAfterRestart() {
    var fixture = createFixture();
    var record =
        headRecord(
            fixture.groupChatId().asString(),
            "MODIFY",
            List.of(headEvent(1, fixture.created()), headEvent(2, fixture.messagePosted())));

    new ReadModelUpdater(sqlSessionFactory, new InMemoryJournalReader()).update(record);
    var restarted = new ReadModelUpdater(sqlSessionFactory, new InMemoryJournalReader());
    restarted.update(record);

    assertEquals(2L, positionOf(fixture.aid()));
    assertEquals(1, count("SELECT COUNT(*) FROM group_chats WHERE id = ?", fixture.aid()));
    assertEquals(1, count("SELECT COUNT(*) FROM messages WHERE group_chat_id = ?", fixture.aid()));

    restarted.update(
        headRecord(
            fixture.groupChatId().asString(),
            "MODIFY",
            List.of(headEvent(3, fixture.messageEdited()))));

    assertEquals(3L, positionOf(fixture.aid()));
    assertEquals("edited", messageText(fixture.messageId()));
  }

  @Test
  void ignoresConfigOtherAggregateTypesAndRemoves() {
    var updater = new ReadModelUpdater(sqlSessionFactory, new InMemoryJournalReader());

    updater.update(record("__config__", "INSERT", Map.of("aid", stringValue("__config__"))));
    updater.update(
        record(
            "UserAccount-01H42K4ABWQ5V2XQEP3A48VE0Z",
            "INSERT",
            Map.of("aid", stringValue("UserAccount-01H42K4ABWQ5V2XQEP3A48VE0Z"))));
    updater.update(record("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z", "REMOVE", null));

    assertNull(positionOf("__config__"));
    assertNull(positionOf("UserAccount-01H42K4ABWQ5V2XQEP3A48VE0Z"));
    assertNull(positionOf("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z"));
    assertEquals(0, count("SELECT COUNT(*) FROM group_chats"));
  }

  @Test
  void rejectsAnUnexpectedStreamEventName() {
    var updater = new ReadModelUpdater(sqlSessionFactory, new InMemoryJournalReader());

    assertThrows(
        ReadModelUpdateException.class,
        () -> updater.update(record("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z", "BROKEN", null)));
  }

  private Fixture createFixture() {
    var groupChatId = GroupChatId.generate();
    var adminId = UserAccountId.generate();
    var memberId = UserAccountId.generate();
    var createResult = GroupChat.create(groupChatId, GroupChatName.of("group-chat"), adminId);
    var created = createResult._2;
    var groupChat = createResult._1;

    var memberAddedResult =
        groupChat.addMember(Member.of(MemberId.generate(), memberId, MemberRole.MEMBER), adminId);
    var memberAdded = memberAddedResult.get()._2;
    var groupChatWithMember = memberAddedResult.get()._1;

    var messageId = MessageId.generate();
    var message = Message.of(messageId, "hello", adminId, LocalDateTime.now());
    var messagePostedResult = groupChatWithMember.postMessage(message, adminId);
    var messagePosted = messagePostedResult.get()._2;
    var groupChatWithMessage = messagePostedResult.get()._1;

    var messageEdited = groupChatWithMessage.editMessage(messageId, "edited", adminId).get()._2;

    return new Fixture(groupChatId, messageId, created, memberAdded, messagePosted, messageEdited);
  }

  private HeadEvent headEvent(long seqNr, GroupChatEvent event) {
    return new HeadEvent(
        seqNr, BASE_TIME.plusSeconds(seqNr), event.manifest(), serializer.serialize(event));
  }

  private HeadEvent invalidHeadEvent(long seqNr) {
    return new HeadEvent(
        seqNr,
        BASE_TIME.plusSeconds(seqNr),
        "GroupChatMessagePosted@1",
        "not-json".getBytes(StandardCharsets.UTF_8));
  }

  private static DynamodbEvent headRecord(
      String aggregateId, String eventName, List<HeadEvent> events) {
    var newImage = new LinkedHashMap<String, AttributeValue>();
    newImage.put("aid", stringValue(aggregateId));
    newImage.put("type_name", stringValue("GroupChat"));
    if (events != null) {
      var seqNr = events.stream().mapToLong(HeadEvent::seqNr).max().orElse(0L);
      newImage.put("seq_nr", numberValue(seqNr));
      var eventList = new ArrayList<AttributeValue>();
      for (var event : events) {
        var fields = new LinkedHashMap<String, AttributeValue>();
        fields.put("seq_nr", numberValue(event.seqNr()));
        fields.put(
            "occurred_at",
            numberValue(
                event.occurredAt().getEpochSecond() * 1_000_000_000L
                    + event.occurredAt().getNano()));
        fields.put("manifest", stringValue(event.manifest()));
        var payload = new AttributeValue();
        payload.setB(ByteBuffer.wrap(event.payload()));
        fields.put("payload", payload);
        var item = new AttributeValue();
        item.setM(fields);
        eventList.add(item);
      }
      var eventsAttribute = new AttributeValue();
      eventsAttribute.setL(eventList);
      newImage.put("events", eventsAttribute);
    }
    return record(aggregateId, eventName, newImage);
  }

  private static DynamodbEvent record(
      String aggregateId, String eventName, Map<String, AttributeValue> newImage) {
    var streamRecord =
        new com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord();
    var keys = new LinkedHashMap<String, AttributeValue>();
    keys.put("aid", stringValue(aggregateId));
    streamRecord.setKeys(keys);
    streamRecord.setNewImage(newImage);
    var record = new DynamodbEvent.DynamodbStreamRecord();
    record.setEventName(eventName);
    record.setEventID("event-" + aggregateId + "-" + eventName);
    record.setDynamodb(streamRecord);
    var event = new DynamodbEvent();
    event.setRecords(List.of(record));
    return event;
  }

  private static AttributeValue stringValue(String value) {
    var attribute = new AttributeValue();
    attribute.setS(value);
    return attribute;
  }

  private static AttributeValue numberValue(long value) {
    var attribute = new AttributeValue();
    attribute.setN(Long.toString(value));
    return attribute;
  }

  private Long positionOf(String aggregateId) {
    var positions =
        jdbcTemplate.queryForList(
            "SELECT seq_nr FROM read_model_positions WHERE aggregate_id = ?",
            Long.class,
            aggregateId);
    return positions.isEmpty() ? null : positions.get(0);
  }

  private String groupChatName(GroupChatId groupChatId) {
    return jdbcTemplate.queryForObject(
        "SELECT name FROM group_chats WHERE id = ?", String.class, groupChatId.asString());
  }

  private String messageText(MessageId messageId) {
    return jdbcTemplate.queryForObject(
        "SELECT text FROM messages WHERE id = ?", String.class, messageId.asString());
  }

  private int count(String sql, Object... args) {
    var result = jdbcTemplate.queryForObject(sql, Integer.class, args);
    return result == null ? 0 : result;
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

  private record Fixture(
      GroupChatId groupChatId,
      MessageId messageId,
      GroupChatEvent created,
      GroupChatEvent memberAdded,
      GroupChatEvent messagePosted,
      GroupChatEvent messageEdited) {
    String aid() {
      return groupChatId.asString();
    }
  }

  private static final class InMemoryJournalReader implements JournalReader {
    private final Map<String, Map<Long, HeadEvent>> events = new HashMap<>();

    void put(String aggregateId, HeadEvent event) {
      events.computeIfAbsent(aggregateId, key -> new HashMap<>()).put(event.seqNr(), event);
    }

    @Override
    public List<HeadEvent> readEvents(String aggregateId, long fromSeqNr, long toSeqNr) {
      return events.getOrDefault(aggregateId, Map.of()).values().stream()
          .filter(event -> event.seqNr() >= fromSeqNr && event.seqNr() <= toSeqNr)
          .sorted(Comparator.comparingLong(HeadEvent::seqNr))
          .toList();
    }
  }
}
