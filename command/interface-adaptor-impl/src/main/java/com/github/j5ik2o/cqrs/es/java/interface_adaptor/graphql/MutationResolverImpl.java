package com.github.j5ik2o.cqrs.es.java.interface_adaptor.graphql;

import org.springframework.stereotype.Component;

@Component
public class MutationResolverImpl implements MutationResolver {
  @Override
  public GroupChatOutput createGroupChat(CreateGroupChatInput input) throws Exception {
    return new GroupChatOutput("aaaa");
  }

  @Override
  public GroupChatOutput deleteGroupChat(DeleteGroupChatInput input) throws Exception {
    return new GroupChatOutput("aaaa");
  }

  @Override
  public GroupChatOutput renameGroupChat(RenameGroupChatInput input) throws Exception {
    return new GroupChatOutput("aaaa");
  }

  @Override
  public GroupChatOutput addMember(AddMemberInput input) throws Exception {
    return new GroupChatOutput("aaaa");
  }

  @Override
  public GroupChatOutput removeMember(RemoveMemberInput input) throws Exception {
    return new GroupChatOutput("aaaa");
  }

  @Override
  public MessageOutput postMessage(PostMessageInput input) throws Exception {
    return null;
  }

  @Override
  public GroupChatOutput deleteMessage(DeleteMessageInput input) throws Exception {
    return null;
  }
}
