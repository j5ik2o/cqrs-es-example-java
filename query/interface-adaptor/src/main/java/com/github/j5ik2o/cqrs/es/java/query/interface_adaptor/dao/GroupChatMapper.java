package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GroupChatMapper {
  @Select(
      """
           SELECT gc.id, gc.name, gc.owner_id, gc.created_at, gc.updated_at
           FROM group_chats AS gc JOIN members AS m ON gc.id = m.group_chat_id
           WHERE gc.disabled = 'false' AND m.group_chat_id = #{groupChatId} AND m.user_account_id = #{userAccountId}
           """)
  Optional<GroupChatRecord> getGroupChat(String groupChatId, String userAccountId);

  @Select(
      """
           SELECT gc.id, gc.name, gc.owner_id, gc.created_at, gc.updated_at
           FROM group_chats AS gc JOIN members AS m ON gc.id = m.group_chat_id
           WHERE gc.disabled = 'false' AND m.user_account_id = #{userAccountId}
           """)
  List<GroupChatRecord> getGroupChats(String userAccountId);
}
