package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Keeps the last applied sequence number per aggregate. The position is written in the same
 * transaction as the read model, so a restart or a retried batch resumes at the last committed
 * event.
 */
@Mapper
public interface ReadModelPositionMapper {

  @Select("SELECT seq_nr FROM read_model_positions WHERE aggregate_id = #{aggregateId}")
  Long selectSeqNr(@Param("aggregateId") String aggregateId);

  /**
   * Makes the row exist before it is locked. A {@code SELECT ... FOR UPDATE} on a missing row takes
   * a gap lock, and two new aggregates in the same gap can deadlock when both insert.
   *
   * <p>{@code INSERT ... ON DUPLICATE KEY UPDATE} takes the row lock in one statement, which is
   * safer than {@code INSERT IGNORE} followed by a lock upgrade when two readers create the same
   * position row.
   */
  @Insert(
      "INSERT INTO read_model_positions (aggregate_id, seq_nr, updated_at) VALUES (#{aggregateId}, 0, #{updatedAt}) "
          + "ON DUPLICATE KEY UPDATE aggregate_id = aggregate_id")
  int insertIfAbsent(
      @Param("aggregateId") String aggregateId, @Param("updatedAt") Instant updatedAt);

  @Select("SELECT seq_nr FROM read_model_positions WHERE aggregate_id = #{aggregateId} FOR UPDATE")
  Long selectSeqNrForUpdate(@Param("aggregateId") String aggregateId);

  @Insert(
      "INSERT INTO read_model_positions (aggregate_id, seq_nr, updated_at) VALUES (#{aggregateId}, #{seqNr}, #{updatedAt}) "
          + "ON DUPLICATE KEY UPDATE seq_nr = VALUES(seq_nr), updated_at = VALUES(updated_at)")
  int upsert(
      @Param("aggregateId") String aggregateId,
      @Param("seqNr") long seqNr,
      @Param("updatedAt") Instant updatedAt);
}
