package com.github.j5ik2o.cqrs.es.java.rmu;

import com.github.j5ik2o.cqrs.es.java.rmu.dao.RecoveryTaskMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;

/** MySQL implementation of the recovery task store. */
public final class MyBatisRecoveryTaskStore implements RecoveryTaskStore {
  private final SqlSessionFactory sqlSessionFactory;

  public MyBatisRecoveryTaskStore(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  @Override
  public void record(RecoveryTask task) {
    var now = Instant.now();
    var createdAt = task.createdAt() == null ? now : task.createdAt();
    var updatedAt = task.updatedAt() == null ? now : task.updatedAt();
    try (var session = sqlSessionFactory.openSession(true)) {
      session
          .getMapper(RecoveryTaskMapper.class)
          .upsert(
              task.storeId(),
              task.projectionName(),
              task.projectionVersion(),
              task.aggregateId(),
              task.seqNr(),
              task.failureKind().asString(),
              task.errorMessage() == null ? "" : task.errorMessage(),
              RecoveryTask.STATUS_PENDING,
              createdAt,
              updatedAt);
    }
  }

  @Override
  public List<RecoveryTask> listPending() {
    try (var session = sqlSessionFactory.openSession(true)) {
      var rows = session.getMapper(RecoveryTaskMapper.class).selectPending();
      var tasks = new ArrayList<RecoveryTask>(rows.size());
      for (var row : rows) {
        tasks.add(toTask(row));
      }
      return tasks;
    }
  }

  @Override
  public void markCompleted(String storeId, String projectionName, String aggregateId, long seqNr) {
    try (var session = sqlSessionFactory.openSession(true)) {
      session
          .getMapper(RecoveryTaskMapper.class)
          .markCompleted(storeId, projectionName, aggregateId, seqNr, Instant.now());
    }
  }

  private static RecoveryTask toTask(Map<String, Object> row) {
    return new RecoveryTask(
        stringValue(row, "store_id"),
        stringValue(row, "projection_name"),
        stringValue(row, "projection_version"),
        stringValue(row, "aggregate_id"),
        numberValue(row, "seq_nr"),
        FailureKind.fromString(stringValue(row, "failure_type")),
        stringValue(row, "error_message"),
        stringValue(row, "status"),
        null,
        null);
  }

  private static String stringValue(Map<String, Object> row, String name) {
    var value = row.get(name);
    return value == null ? "" : value.toString();
  }

  private static long numberValue(Map<String, Object> row, String name) {
    var value = row.get(name);
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw new ReadModelUpdateException("recovery task row has no numeric " + name);
  }
}
