package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;

public abstract class GroupChatError extends RuntimeException {
  protected GroupChatError(String message, Throwable cause) {
    super(message, cause);
  }

  protected GroupChatError(String message) {
    super(message);
  }

  public static final class AlreadyDeletedError extends GroupChatError {
    public AlreadyDeletedError(GroupChatId id) {
      super(String.format("GroupChat(id = %s) is already deleted.", id));
    }
  }

  public static final class NotMemberError extends GroupChatError {
    public NotMemberError(GroupChatId id, UserAccountId executorId) {
      super(
          String.format(
              "UserAccount(id = %s) is not member of GroupChat(id = %s).", executorId, id));
    }
  }

  public static class AlreadyRenamedError extends GroupChatError {
    public AlreadyRenamedError(GroupChatId id, GroupChatName name) {
      super(String.format("GroupChat(id = %s) is already renamed to %s.", id, name));
    }
  }

  public static class NotAdministratorError extends GroupChatError {
    public NotAdministratorError(GroupChatId id, UserAccountId executorId) {
      super(
          String.format(
              "UserAccount(id = %s) is not administrator of GroupChat(id = %s).", executorId, id));
    }
  }

  public static class AlreadyMemberError extends GroupChatError {
    public AlreadyMemberError(GroupChatId id, Object userAccountId) {
      super(
          String.format(
              "UserAccount(id = %s) is already member of GroupChat(id = %s).", userAccountId, id));
    }
  }

  public static class MessageNotFoundError extends GroupChatError {
    public MessageNotFoundError(GroupChatId id, MessageId messageId) {
      super(String.format("Message(id = %s) is not found in GroupChat(id = %s).", messageId, id));
    }
  }
}
