package com.github.j5ik2o.cqrs.es.java.domain.useraccount;

import com.github.f4b6a3.ulid.Ulid;
import com.github.j5ik2o.event.store.adapter.java.AggregateId;
import javax.annotation.Nonnull;

public class UserAccountId implements AggregateId {
  private static final String TYPE_NAME = "UserAccount";

  private final Ulid value;

  private UserAccountId(Ulid value) {
    this.value = value;
  }

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

  public static UserAccountId of(Ulid value) {
    return new UserAccountId(value);
  }

  public static UserAccountId of(String value) {
    if (value.startsWith(TYPE_NAME + "-")) {
      value = value.substring((TYPE_NAME + "-").length());
    }
    return new UserAccountId(Ulid.from(value));
  }
}
