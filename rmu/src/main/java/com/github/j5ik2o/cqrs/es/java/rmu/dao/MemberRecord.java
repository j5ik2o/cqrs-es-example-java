package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import java.time.Instant;

public record MemberRecord(
    String id,
    String groupChatId,
    String userAccountId,
    String role,
    Instant createdAt,
    Instant updatedAt) {}
