package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao;

import java.time.LocalDateTime;

public record GroupChatRecord(
    String id, String name, String ownerId, LocalDateTime createdAt, LocalDateTime updatedAt) {}
