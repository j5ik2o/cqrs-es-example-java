package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import java.util.List;

/** One page of records read from a shard iterator. */
public record StreamRecordPage(
    List<DynamodbEvent.DynamodbStreamRecord> records,
    List<String> sequenceNumbers,
    String nextShardIterator) {}
