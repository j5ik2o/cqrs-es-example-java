package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao;

import java.time.LocalDateTime;

public record MessageRecord(
    String id,
    String groupChatId,
    String userAccountId,
    String text,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
