package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class Member {
  private final MemberId id;
  private final UserAccountId userAccountId;

  private final MemberRole role;

  private Member(MemberId id, UserAccountId userAccountId, MemberRole role) {
    this.id = id;
    this.userAccountId = userAccountId;
    this.role = role;
  }

  @Nonnull
  public MemberId getId() {
    return id;
  }

  @Nonnull
  public UserAccountId getUserAccountId() {
    return userAccountId;
  }

  @Nonnull
  public MemberRole getRole() {
    return role;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Member member = (Member) o;
    return Objects.equals(id, member.id)
        && Objects.equals(userAccountId, member.userAccountId)
        && role == member.role;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, userAccountId, role);
  }

  @Override
  public String toString() {
    return "Member{" + "id=" + id + ", userAccountId=" + userAccountId + ", role=" + role + '}';
  }

  public static Member of(MemberId id, UserAccountId userAccountId, MemberRole role) {
    return new Member(id, userAccountId, role);
  }
}
