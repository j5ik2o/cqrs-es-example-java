package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MemberWriteMapper {
  @Insert(
      "INSERT INTO members (id, group_chat_id, user_account_id, role, created_at, updated_at) VALUES(#{id}, #{groupChatId}, #{userAccountId}, #{role}, #{createdAt}, #{updatedAt})")
  @Flush
  int insertMember(MemberRecord memberRecord);

  @Update(
      "UPDATE members SET group_chat_id = #{groupChatId}, user_account_id = #{userAccountId}, updated_at = #{updatedAt} WHERE id = #{id}")
  @Flush
  int updateMember(MemberRecord memberRecord);

  @Delete(
      "DELETE FROM members WHERE group_chat_id = #{groupChatId} AND user_account_id = #{userAccountId}")
  @Flush
  int deleteMember(
      @Param("groupChatId") String groupChatId, @Param("userAccountId") String userAccountId);
}
