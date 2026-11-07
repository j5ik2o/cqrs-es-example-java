package com.github.j5ik2o.cqrs.es.java.main;

import com.github.j5ik2o.cqrs.es.java.rmu.Applier;
import com.github.j5ik2o.cqrs.es.java.rmu.Backoff;
import com.github.j5ik2o.cqrs.es.java.rmu.DynamoDBHeadScanner;
import com.github.j5ik2o.cqrs.es.java.rmu.DynamoDBHeadStreamClient;
import com.github.j5ik2o.cqrs.es.java.rmu.EventApplier;
import com.github.j5ik2o.cqrs.es.java.rmu.HeadStreamClient;
import com.github.j5ik2o.cqrs.es.java.rmu.HeadStreamPoller;
import com.github.j5ik2o.cqrs.es.java.rmu.JournalReader;
import com.github.j5ik2o.cqrs.es.java.rmu.MyBatisRecoveryTaskStore;
import com.github.j5ik2o.cqrs.es.java.rmu.RecoveryService;
import com.github.j5ik2o.cqrs.es.java.rmu.RecoveryTaskStore;
import com.github.j5ik2o.cqrs.es.java.rmu.Resyncer;
import com.github.j5ik2o.cqrs.es.java.rmu.RetryInterruptedException;
import com.github.j5ik2o.cqrs.es.java.rmu.RetryPolicy;
import java.time.Clock;
import java.time.Duration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

/** Wires the recovery, retry, and resync components for the local read model updater. */
@Configuration
@Profile("local-rmu")
public class RmuConfig {

  @Bean
  public RetryPolicy rmuRetryPolicy(AppConfig appConfig) {
    var stream = appConfig.getStream();
    return new RetryPolicy(
        stream.getRetryMaxAttempts(),
        Duration.ofMillis(stream.getRetryMaxElapsedMillis()),
        Duration.ofMillis(stream.getRetryInitialDelayMillis()),
        Duration.ofMillis(stream.getRetryMaxDelayMillis()),
        delay -> {
          try {
            Thread.sleep(delay.toMillis());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryInterruptedException(e);
          }
        },
        null,
        Clock.systemUTC());
  }

  @Bean
  public Backoff streamPollBackoff(AppConfig appConfig) {
    var stream = appConfig.getStream();
    return new Backoff(
        Duration.ofMillis(stream.getPollBackoffInitialMillis()),
        Duration.ofMillis(stream.getPollBackoffMaxMillis()));
  }

  @Bean
  public DynamoDBHeadScanner headScanner(DynamoDbClient dynamoDbClient, AppConfig appConfig) {
    return new DynamoDBHeadScanner(dynamoDbClient, appConfig.getStream().getHeadTableName());
  }

  @Bean
  public RecoveryTaskStore recoveryTaskStore(SqlSessionFactory sqlSessionFactory) {
    return new MyBatisRecoveryTaskStore(sqlSessionFactory);
  }

  @Bean
  public EventApplier eventApplier(
      SqlSessionFactory sqlSessionFactory, JournalReader journalReader, AppConfig appConfig) {
    return new Applier(
        sqlSessionFactory, journalReader, appConfig.getStream().getMaxEventsPerTransaction());
  }

  @Bean
  public RecoveryService recoveryService(
      RecoveryTaskStore recoveryTaskStore,
      DynamoDBHeadScanner headScanner,
      EventApplier eventApplier) {
    return new RecoveryService(recoveryTaskStore, headScanner, eventApplier, null);
  }

  @Bean
  public Resyncer resyncer(
      EventApplier eventApplier, DynamoDBHeadScanner headScanner, RecoveryService recoveryService) {
    return new Resyncer(eventApplier, headScanner, recoveryService);
  }

  @Bean
  public HeadStreamClient headStreamClient(
      DynamoDbClient dynamoDbClient, DynamoDbStreamsClient dynamoDbStreamsClient) {
    return new DynamoDBHeadStreamClient(dynamoDbClient, dynamoDbStreamsClient);
  }

  @Bean
  public HeadStreamPoller headStreamPoller(
      HeadStreamClient headStreamClient,
      com.github.j5ik2o.cqrs.es.java.rmu.ReadModelUpdater readModelUpdater,
      AppConfig appConfig) {
    return new HeadStreamPoller(
        headStreamClient, readModelUpdater::updateRecord, appConfig.getStream().getMaxItemCount());
  }
}
