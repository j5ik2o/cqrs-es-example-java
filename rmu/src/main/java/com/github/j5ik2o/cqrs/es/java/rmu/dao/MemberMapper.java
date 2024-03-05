package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import org.apache.ibatis.annotations.*;

@Mapper
public interface MemberMapper {
  @Insert(
      "INSERT INTO members (id, group_chat_id, user_account_id, created_at, updated_at) VALUES(#{id}, #{groupChatId}, #{userAccountId}, #{createdAt}, #{updatedAt})")
  @Flush
  int insertMember(MemberRecord memberRecord);

  @Update(
      "UPDATE members SET group_chat_id = #{groupChatId}, user_account_id = #{userAccountId}, updated_at = #{updatedAt} WHERE id = #{id}")
  @Flush
  int updateMember(MemberRecord memberRecord);

  @Delete(
      "DELETE FROM members WHERE group_chat_id = #{groupChatId} AND user_account_id = #{userAccountId}")
  @Flush
  int deleteMember(String groupChatId, String userAccountId);
}
