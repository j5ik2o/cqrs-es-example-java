package com.github.j5ik2o.cqrs.es.java.domain.useraccount;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;
import com.github.j5ik2o.event.store.adapter.java.AggregateId;
import io.vavr.control.Either;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class UserAccountId implements AggregateId {
  private static final String TYPE_NAME = "UserAccount";

  @JsonProperty("value")
  @Nonnull
  private final Ulid value;

  private UserAccountId(@Nonnull @JsonProperty("value") Ulid value) {
    this.value = value;
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Nonnull
  @Override
  public String getTypeName() {
    return TYPE_NAME;
  }

  @Nonnull
  @Override
  public String getValue() {
    return value.toString();
  }

  @Nonnull
  @Override
  public String asString() {
    return String.format("%s-%s", getTypeName(), value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UserAccountId that = (UserAccountId) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "UserAccountId{" + "value=" + value + '}';
  }

  public static UserAccountId of(Ulid value) {
    return new UserAccountId(value);
  }

  public static UserAccountId ofString(String value) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("userAccountId is empty");
    }
    if (value.startsWith(TYPE_NAME + "-")) {
      value = value.substring((TYPE_NAME + "-").length());
    }
    return new UserAccountId(Ulid.from(value));
  }

  public static UserAccountId generate() {
    return UserAccountId.of(UlidCreator.getMonotonicUlid());
  }

  public static Either<IllegalArgumentException, UserAccountId> validate(String value) {
    try {
      return Either.right(UserAccountId.ofString(value));
    } catch (Exception e) {
      return Either.left(new IllegalArgumentException(e));
    }
  }
}
