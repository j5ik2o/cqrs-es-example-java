package com.github.j5ik2o.cqrs.es.java.rmu;

import java.util.function.Consumer;

/**
 * Walks the whole head table. DY-15 requires a strongly consistent read so a resync sees every
 * aggregate that advanced while the reader was stopped.
 */
public interface HeadScanner {
  void scanHeads(Consumer<Head> visitor);
}
