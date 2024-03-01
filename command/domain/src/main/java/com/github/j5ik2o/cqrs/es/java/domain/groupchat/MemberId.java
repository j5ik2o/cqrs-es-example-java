package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class MemberId {
  private final Ulid value;

  private MemberId(Ulid value) {
    this.value = value;
  }

  public String asString() {
    return value.toString();
  }

  @Nonnull
  public Ulid toUlid() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MemberId memberId = (MemberId) o;
    return Objects.equals(value, memberId.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "MemberId{" + "value=" + value + '}';
  }

  public static MemberId of(Ulid value) {
    return new MemberId(value);
  }

  public static MemberId of(String value) {
    return new MemberId(Ulid.from(value));
  }

  public static MemberId generate() {
    return new MemberId(UlidCreator.getMonotonicUlid());
  }
}
