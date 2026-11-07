package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Stores the recovery tasks that the RMU could not apply.
 *
 * <p>The primary key makes recording idempotent: a redelivered stream record updates the existing
 * pending task instead of creating a second one.
 */
@Mapper
public interface RecoveryTaskMapper {

  @Insert(
      "INSERT INTO read_model_recovery_tasks "
          + "(store_id, projection_name, projection_version, aggregate_id, seq_nr, failure_type, error_message, status, created_at, updated_at) "
          + "VALUES (#{storeId}, #{projectionName}, #{projectionVersion}, #{aggregateId}, #{seqNr}, #{failureType}, #{errorMessage}, #{status}, #{createdAt}, #{updatedAt}) "
          + "ON DUPLICATE KEY UPDATE projection_version = VALUES(projection_version), "
          + "failure_type = VALUES(failure_type), error_message = VALUES(error_message), "
          + "status = VALUES(status), updated_at = VALUES(updated_at)")
  int upsert(
      @Param("storeId") String storeId,
      @Param("projectionName") String projectionName,
      @Param("projectionVersion") String projectionVersion,
      @Param("aggregateId") String aggregateId,
      @Param("seqNr") long seqNr,
      @Param("failureType") String failureType,
      @Param("errorMessage") String errorMessage,
      @Param("status") String status,
      @Param("createdAt") Instant createdAt,
      @Param("updatedAt") Instant updatedAt);

  @Select(
      "SELECT store_id, projection_name, projection_version, aggregate_id, seq_nr, failure_type, "
          + "error_message, status FROM read_model_recovery_tasks "
          + "WHERE status = 'pending' ORDER BY created_at, aggregate_id, seq_nr")
  List<Map<String, Object>> selectPending();

  @Update(
      "UPDATE read_model_recovery_tasks SET status = 'completed', updated_at = #{updatedAt} "
          + "WHERE store_id = #{storeId} AND projection_name = #{projectionName} "
          + "AND aggregate_id = #{aggregateId} AND seq_nr = #{seqNr}")
  int markCompleted(
      @Param("storeId") String storeId,
      @Param("projectionName") String projectionName,
      @Param("aggregateId") String aggregateId,
      @Param("seqNr") long seqNr,
      @Param("updatedAt") Instant updatedAt);
}
