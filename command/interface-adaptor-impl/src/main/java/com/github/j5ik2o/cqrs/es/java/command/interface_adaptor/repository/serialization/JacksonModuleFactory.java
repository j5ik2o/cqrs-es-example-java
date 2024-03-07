package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.f4b6a3.ulid.Ulid;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Members;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Messages;

public class JacksonModuleFactory {
  public static Module create() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(Ulid.class, new UlidJsonSerializer());
    module.addDeserializer(Ulid.class, new UlidJsonDeserializer());
    module.addSerializer(Members.class, new MembersJsonSerializer());
    module.addDeserializer(Members.class, new MembersJsonDeserializer());
    module.addSerializer(Messages.class, new MessagesJsonSerializer());
    module.addDeserializer(Messages.class, new MessagesJsonDeserializer());
    return module;
  }
}
