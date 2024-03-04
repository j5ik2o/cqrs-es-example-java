package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import io.vavr.control.Either;

public enum MemberRole {
  MEMBER,
  ADMINISTRATOR;

  public static Either<IllegalArgumentException, MemberRole> validate(String value) {
    try {
      return Either.right(MemberRole.valueOf(value));
    } catch (IllegalArgumentException e) {
      return Either.left(e);
    }
  }
}
