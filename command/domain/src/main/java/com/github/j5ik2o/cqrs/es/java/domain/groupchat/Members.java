package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import io.vavr.Tuple2;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class Members {
  @JsonProperty("values")
  private final Vector<Member> values;

  private Members(@Nonnull @JsonProperty("values") Vector<Member> values) {
    this.values = values;
  }

  public Members add(Member value) {
    return new Members(values.append(value));
  }

  public Option<Tuple2<Member, Members>> removeByUserAccountId(UserAccountId userAccountId) {
    return values
        .find(member -> member.getUserAccountId().equals(userAccountId))
        .map(member -> new Tuple2<>(member, new Members(values.remove(member))));
  }

  public Option<Member> findByUserAccountId(UserAccountId userAccountId) {
    return values.find(member -> member.getUserAccountId().equals(userAccountId));
  }

  public boolean containsByUserAccountId(UserAccountId userAccountId) {
    return values.exists(member -> member.getUserAccountId().equals(userAccountId));
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public boolean isMember(UserAccountId userAccountId) {
    return values.exists(
        member ->
            member.getUserAccountId().equals(userAccountId)
                && member.getRole() == MemberRole.MEMBER);
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public boolean isAdministrator(UserAccountId userAccountId) {
    return values.exists(
        member ->
            member.getUserAccountId().equals(userAccountId)
                && member.getRole() == MemberRole.ADMINISTRATOR);
  }

  public Vector<Member> toVector() {
    return values;
  }

  public int size() {
    return values.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Members members = (Members) o;
    return Objects.equals(values, members.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(values);
  }

  @Override
  public String toString() {
    return "Members{" + "values=" + values + '}';
  }

  public static Members of(Member head, Member... tail) {
    return new Members(Vector.of(tail).prepend(head));
  }

  public static Members from(Vector<Member> values) {
    return new Members(values);
  }

  public static Members ofAdministratorId(UserAccountId userAccountId) {
    return Members.of(Member.of(MemberId.generate(), userAccountId, MemberRole.ADMINISTRATOR));
  }
}
