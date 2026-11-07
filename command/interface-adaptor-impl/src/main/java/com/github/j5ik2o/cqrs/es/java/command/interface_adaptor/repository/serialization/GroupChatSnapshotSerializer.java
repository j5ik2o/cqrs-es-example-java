package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.event.store.adapter.java.core.PayloadSerializer;
import com.github.j5ik2o.event.store.adapter.java.core.SerializationException;
import java.io.IOException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GroupChatSnapshotSerializer implements PayloadSerializer<GroupChat> {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.registerModule(JacksonModuleFactory.create());
    OBJECT_MAPPER.findAndRegisterModules();
  }

  @Nonnull
  @Override
  public byte[] serialize(@Nonnull GroupChat groupChat) throws SerializationException {
    try {
      return OBJECT_MAPPER.writeValueAsBytes(groupChat);
    } catch (JsonProcessingException e) {
      throw new SerializationException("failed to serialize GroupChat", e);
    }
  }

  @Nullable
  @Override
  public GroupChat deserialize(@Nonnull byte[] bytes) throws SerializationException {
    try {
      return OBJECT_MAPPER.readValue(bytes, GroupChat.class);
    } catch (IOException e) {
      throw new SerializationException("failed to deserialize GroupChat", e);
    }
  }
}
