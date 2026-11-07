package com.github.j5ik2o.cqrs.es.java.rmu;

import com.github.j5ik2o.cqrs.es.java.rmu.dao.GroupChatWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.MemberWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.MessageWriteMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.ReadModelPositionMapper;
import com.github.j5ik2o.cqrs.es.java.rmu.dao.RecoveryTaskMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Builds the RMU dependencies for the AWS Lambda entry point.
 *
 * <p>Lambda instantiates the handler class with a no-argument constructor, so the Spring wiring in
 * {@code bootstrap} is not available. This class reads the same settings from environment variables
 * and builds the journal reader, head scanner, recovery task store, and transaction applier.
 */
public final class LambdaRmuDependencies {

  public record Dependencies(
      EventApplier applier, RecoveryService recoveryService, RetryPolicy retryPolicy) {}

  private LambdaRmuDependencies() {}

  public static Dependencies create() {
    var headTableName = env("APP_STREAM_HEAD_TABLE_NAME", "head");
    var journalTableName = env("APP_STREAM_JOURNAL_TABLE_NAME", "journal");
    var retryPolicy =
        new RetryPolicy(
            intEnv("APP_STREAM_RETRY_MAX_ATTEMPTS", 5),
            Duration.ofMillis(longEnv("APP_STREAM_RETRY_MAX_ELAPSED_MILLIS", 30_000L)),
            Duration.ofMillis(longEnv("APP_STREAM_RETRY_INITIAL_DELAY_MILLIS", 200L)),
            Duration.ofMillis(longEnv("APP_STREAM_RETRY_MAX_DELAY_MILLIS", 5_000L)),
            null,
            null,
            Clock.systemUTC());

    var dynamoDbClient = createDynamoDbClient();
    var journalReader = new DynamoDBJournalReader(dynamoDbClient, journalTableName);
    var headScanner = new DynamoDBHeadScanner(dynamoDbClient, headTableName);
    var sqlSessionFactory = createSqlSessionFactory();
    var applier =
        new Applier(
            sqlSessionFactory, journalReader, intEnv("APP_STREAM_MAX_EVENTS_PER_TRANSACTION", 1));
    var taskStore = new MyBatisRecoveryTaskStore(sqlSessionFactory);
    var recoveryService = new RecoveryService(taskStore, headScanner, applier, null);
    return new Dependencies(applier, recoveryService, retryPolicy);
  }

  private static DynamoDbClient createDynamoDbClient() {
    var region = env("APP_DYNAMO_DB_REGION", env("AWS_REGION", null));
    if (region == null) {
      return DynamoDbClient.create();
    }
    var builder = DynamoDbClient.builder().region(Region.of(region));
    var endpoint = env("APP_DYNAMO_DB_ENDPOINT_URL", null);
    if (endpoint != null) {
      builder = builder.endpointOverride(URI.create(endpoint));
    }
    var accessKey = env("APP_DYNAMO_DB_ACCESS_KEY_ID", null);
    var secretKey = env("APP_DYNAMO_DB_SECRET_ACCESS_KEY", null);
    if (accessKey != null && secretKey != null) {
      builder =
          builder.credentialsProvider(
              StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
    }
    return builder.build();
  }

  private static SqlSessionFactory createSqlSessionFactory() {
    var url = env("SPRING_DATASOURCE_URL", env("DATABASE_URL", null));
    if (url == null) {
      throw new IllegalStateException("SPRING_DATASOURCE_URL or DATABASE_URL is required");
    }
    var username = env("SPRING_DATASOURCE_USERNAME", env("DATABASE_USERNAME", ""));
    var password = env("SPRING_DATASOURCE_PASSWORD", env("DATABASE_PASSWORD", ""));
    var dataSource = new PooledDataSource("com.mysql.cj.jdbc.Driver", url, username, password);
    var environment = new Environment("lambda", new JdbcTransactionFactory(), dataSource);
    var configuration = new Configuration(environment);
    configuration.addMapper(GroupChatWriteMapper.class);
    configuration.addMapper(MemberWriteMapper.class);
    configuration.addMapper(MessageWriteMapper.class);
    configuration.addMapper(ReadModelPositionMapper.class);
    configuration.addMapper(RecoveryTaskMapper.class);
    return new SqlSessionFactoryBuilder().build(configuration);
  }

  private static String env(String name, String fallback) {
    var value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static int intEnv(String name, int fallback) {
    var value = env(name, null);
    return value == null ? fallback : Integer.parseInt(value);
  }

  private static long longEnv(String name, long fallback) {
    var value = env(name, null);
    return value == null ? fallback : Long.parseLong(value);
  }
}
