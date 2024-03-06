package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import java.time.Instant;

public record MessageRecord(
    String id,
    String groupChatId,
    String content,
    String senderId,
    Instant createdAt,
    Instant updatedAt) {}
