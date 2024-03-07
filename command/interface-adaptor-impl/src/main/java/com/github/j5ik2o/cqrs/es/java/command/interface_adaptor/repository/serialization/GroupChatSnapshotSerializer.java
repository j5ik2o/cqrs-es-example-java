package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.event.store.adapter.java.DeserializationException;
import com.github.j5ik2o.event.store.adapter.java.SerializationException;
import com.github.j5ik2o.event.store.adapter.java.SnapshotSerializer;
import java.io.IOException;
import javax.annotation.Nonnull;

public final class GroupChatSnapshotSerializer
    implements SnapshotSerializer<GroupChatId, GroupChat> {
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
      throw new SerializationException(e);
    }
  }

  @Nonnull
  @Override
  public GroupChat deserialize(@Nonnull byte[] bytes, @Nonnull Class<GroupChat> clazz)
      throws DeserializationException {
    try {
      return OBJECT_MAPPER.readValue(bytes, clazz);
    } catch (IOException e) {
      throw new DeserializationException(e);
    }
  }
}
