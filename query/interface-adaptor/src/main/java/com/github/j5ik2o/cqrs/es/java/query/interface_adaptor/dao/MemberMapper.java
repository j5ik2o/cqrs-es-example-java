package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MemberMapper {

  @Select(
      """
            SELECT m.id, m.group_chat_id, m.user_account_id, m.role, m.created_at, m.updated_at
            FROM group_chats AS gc JOIN members AS m ON gc.id = m.group_chat_id
            WHERE gc.disabled = 'false' AND m.group_chat_id = #{groupChatId} AND m.user_account_id = #{userAccountId}
            """)
  Optional<MemberRecord> getMember(String groupChatId, String userAccountId);

  @Select(
      """
            SELECT m.id, m.group_chat_id, m.user_account_id, m.role, m.created_at, m.updated_at
            FROM group_chats AS gc JOIN members AS m ON gc.id = m.group_chat_id
            WHERE gc.disabled = 'false' AND m.group_chat_id = #{groupChatId}
            AND EXISTS (SELECT 1 FROM members AS m2 WHERE m2.group_chat_id = m.group_chat_id AND m2.user_account_id = #{userAccountId}
            """)
  List<MemberRecord> getMembers(String groupChatId, String userAccountId);
}
