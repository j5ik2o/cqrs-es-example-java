package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao;

import java.time.LocalDateTime;

public record MemberRecord(
    String id,
    String groupChatId,
    String userAccountId,
    String role,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
