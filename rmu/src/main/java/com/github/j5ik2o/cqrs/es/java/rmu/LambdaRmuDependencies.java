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
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Builds the RMU dependencies for the AWS Lambda entry point.
 *
 * <p>Lambda instantiates the handler class with a no-argument constructor, so the Spring wiring in
 * {@code bootstrap} is not available. This class reads the same settings from environment variables
 * and builds the journal reader, head scanner, recovery task store, and transaction applier.
 */
public final class LambdaRmuDependencies {

  public record Dependencies(
      EventApplier applier,
      RecoveryService recoveryService,
      RetryPolicy retryPolicy,
      Resyncer resyncer,
      int maxRecoveryTasksPerRun) {}

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
    var recoveryService =
        new RecoveryService(taskStore, headScanner, applier, createRecoveryNotifier());
    var resyncer = new Resyncer(applier, headScanner, recoveryService);
    return new Dependencies(
        applier,
        recoveryService,
        retryPolicy,
        resyncer,
        positiveIntEnv("APP_RECOVERY_MAX_TASKS_PER_RUN", 50));
  }

  /**
   * The RMU sends a wake-up message to the recovery queue after a task is durably recorded. Without
   * a queue URL the notifier is absent and the recovery worker is driven another way.
   */
  private static RecoveryNotifier createRecoveryNotifier() {
    var queueUrl = env("APP_RECOVERY_QUEUE_URL", null);
    if (queueUrl == null) {
      return null;
    }
    return new SqsRecoveryNotifier(createSqsClient(), queueUrl);
  }

  private static SqsClient createSqsClient() {
    var builder = SqsClient.builder();
    applyClientConfig(
        builder,
        "APP_RECOVERY_SQS_REGION",
        "APP_RECOVERY_SQS_ENDPOINT_URL",
        "APP_RECOVERY_SQS_ACCESS_KEY_ID",
        "APP_RECOVERY_SQS_SECRET_ACCESS_KEY");
    return builder.build();
  }

  private static DynamoDbClient createDynamoDbClient() {
    var builder = DynamoDbClient.builder();
    applyClientConfig(
        builder,
        "APP_DYNAMO_DB_REGION",
        "APP_DYNAMO_DB_ENDPOINT_URL",
        "APP_DYNAMO_DB_ACCESS_KEY_ID",
        "APP_DYNAMO_DB_SECRET_ACCESS_KEY");
    return builder.build();
  }

  /**
   * Applies the region, endpoint, and static credentials of one client. The SQS settings are
   * separate from the DynamoDB ones so a custom DynamoDB endpoint is not sent SQS requests. Unset
   * values are left for the SDK's default provider chain, which is what a Lambda execution role
   * uses.
   */
  private static <B extends AwsClientBuilder<B, ?>> void applyClientConfig(
      B builder,
      String regionName,
      String endpointName,
      String accessKeyName,
      String secretKeyName) {
    var region = env(regionName, env("AWS_REGION", null));
    if (region != null) {
      builder.region(Region.of(region));
    }
    var endpoint = env(endpointName, null);
    if (endpoint != null) {
      builder.endpointOverride(URI.create(endpoint));
    }
    var credentials = staticCredentials(accessKeyName, secretKeyName);
    if (credentials != null) {
      builder.credentialsProvider(credentials);
    }
  }

  private static AwsCredentialsProvider staticCredentials(
      String accessKeyName, String secretKeyName) {
    var accessKey = env(accessKeyName, null);
    var secretKey = env(secretKeyName, null);
    if (accessKey == null || secretKey == null) {
      return null;
    }
    return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
  }

  private static SqlSessionFactory createSqlSessionFactory() {
    var settings = resolveDatabaseSettings();
    var dataSource =
        new PooledDataSource(
            "com.mysql.cj.jdbc.Driver", settings.url(), settings.username(), settings.password());
    var environment = new Environment("lambda", new JdbcTransactionFactory(), dataSource);
    var configuration = new Configuration(environment);
    configuration.addMapper(GroupChatWriteMapper.class);
    configuration.addMapper(MemberWriteMapper.class);
    configuration.addMapper(MessageWriteMapper.class);
    configuration.addMapper(ReadModelPositionMapper.class);
    configuration.addMapper(RecoveryTaskMapper.class);
    return new SqlSessionFactoryBuilder().build(configuration);
  }

  /**
   * Resolves MySQL credentials from Secrets Manager when a secret ARN is configured. The secret is
   * a JSON object with {@code username} and {@code password}; its optional {@code url} overrides
   * {@code SPRING_DATASOURCE_URL}. Without a secret ARN the plain environment variables stay
   * supported for local runs.
   */
  private static DatabaseSettings resolveDatabaseSettings() {
    var url = env("SPRING_DATASOURCE_URL", env("DATABASE_URL", null));
    var secretArn = env("APP_DB_SECRET_ARN", null);
    if (secretArn != null) {
      return DatabaseSettings.fromSecretJson(fetchSecret(secretArn), url);
    }
    return DatabaseSettings.fromEnvironment(
        url,
        env("SPRING_DATASOURCE_USERNAME", env("DATABASE_USERNAME", "")),
        env("SPRING_DATASOURCE_PASSWORD", env("DATABASE_PASSWORD", "")));
  }

  private static String fetchSecret(String secretArn) {
    var builder = SecretsManagerClient.builder();
    applyClientConfig(
        builder,
        "APP_DB_SECRET_REGION",
        "APP_DB_SECRET_ENDPOINT_URL",
        "APP_DB_SECRET_ACCESS_KEY_ID",
        "APP_DB_SECRET_SECRET_ACCESS_KEY");
    try (var client = builder.build()) {
      return client.getSecretValue(request -> request.secretId(secretArn)).secretString();
    }
  }

  private static String env(String name, String fallback) {
    var value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static int intEnv(String name, int fallback) {
    var value = env(name, null);
    return value == null ? fallback : Integer.parseInt(value);
  }

  private static int positiveIntEnv(String name, int fallback) {
    var value = intEnv(name, fallback);
    if (value <= 0) {
      throw new IllegalStateException(name + " must be a positive integer");
    }
    return value;
  }

  private static long longEnv(String name, long fallback) {
    var value = env(name, null);
    return value == null ? fallback : Long.parseLong(value);
  }
}
