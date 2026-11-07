package com.github.j5ik2o.cqrs.es.java.rmu;

/**
 * The stream operations the local reader needs.
 *
 * <p>It is a thin seam over DynamoDB Streams so the iterator state machine can be tested without a
 * container.
 */
public interface HeadStreamClient {
  String streamArn(String headTableName);

  ShardPage describeShards(String streamArn, String exclusiveStartShardId);

  StreamRecordPage getRecords(String streamArn, String shardIterator, int limit);

  String shardIteratorAtTrimHorizon(String streamArn, String shardId);

  String shardIteratorAfterSequenceNumber(String streamArn, String shardId, String sequenceNumber);
}
