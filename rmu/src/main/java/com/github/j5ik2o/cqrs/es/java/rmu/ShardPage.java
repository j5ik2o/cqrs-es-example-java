package com.github.j5ik2o.cqrs.es.java.rmu;

import java.util.List;

/** One page of a shard listing. */
public record ShardPage(List<String> shardIds, String lastEvaluatedShardId) {}
