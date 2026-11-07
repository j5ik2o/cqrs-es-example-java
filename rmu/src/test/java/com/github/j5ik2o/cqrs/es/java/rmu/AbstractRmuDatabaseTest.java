package com.github.j5ik2o.cqrs.es.java.rmu;

import com.github.j5ik2o.cqrs.es.java.rmu.dao.GroupChatWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.MemberWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.MessageWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.ReadModelPositionMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.RecoveryTaskMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared MySQL fixture for the recovery and resync tests. */
abstract class AbstractRmuDatabaseTest {

  private static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:26.10"))
          .withConfigurationOverride("mysql-conf")
          .withDatabaseName("ceer")
          .withUsername("ceer")
          .withPassword("ceer");

  static {
    // A singleton container is shared by every test class in this JVM. The
    // Testcontainers extension stops a @Container after the first class, so the
    // container is started here and left to Ryuk at JVM shutdown.
    MYSQL.start();
  }

  protected static SqlSessionFactory sqlSessionFactory;
  protected static JdbcTemplate jdbcTemplate;

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
    configuration.addMapper(RecoveryTaskMapper.class);
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.update("DELETE FROM read_model_recovery_tasks");
    jdbcTemplate.update("DELETE FROM messages");
    jdbcTemplate.update("DELETE FROM members");
    jdbcTemplate.update("DELETE FROM group_chats");
    jdbcTemplate.update("DELETE FROM read_model_positions");
  }

  protected static Long positionOf(String aggregateId) {
    var positions =
        jdbcTemplate.queryForList(
            "SELECT seq_nr FROM read_model_positions WHERE aggregate_id = ?",
            Long.class,
            aggregateId);
    return positions.isEmpty() ? null : positions.get(0);
  }

  protected static int count(String sql, Object... args) {
    var result = jdbcTemplate.queryForObject(sql, Integer.class, args);
    return result == null ? 0 : result;
  }

  protected static String messageText(String messageId) {
    return jdbcTemplate.queryForObject(
        "SELECT text FROM messages WHERE id = ?", String.class, messageId);
  }

  protected static RetryPolicy testRetryPolicy(int maxAttempts) {
    return new RetryPolicy(
        maxAttempts,
        Duration.ofSeconds(30),
        Duration.ZERO,
        Duration.ZERO,
        delay -> {},
        delay -> delay,
        Clock.systemUTC());
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
