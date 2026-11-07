package com.github.j5ik2o.cqrs.es.java.rmu;

import java.time.Duration;

/** Waits between retry attempts. Tests use a fake so they do not spend real time. */
@FunctionalInterface
public interface Sleeper {
  void sleep(Duration delay);
}
