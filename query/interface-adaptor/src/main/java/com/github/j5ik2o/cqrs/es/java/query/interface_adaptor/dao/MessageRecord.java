package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao;

import java.time.LocalDateTime;

public record MessageRecord(
    String id,
    String groupChatId,
    String content,
    String senderId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
