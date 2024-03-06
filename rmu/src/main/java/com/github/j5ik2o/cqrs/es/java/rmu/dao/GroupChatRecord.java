package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import java.time.Instant;

public record GroupChatRecord(
    String id, String name, String ownerId, Instant createdAt, Instant updatedAt) {}
