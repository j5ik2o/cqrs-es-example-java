package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs a full head resync on demand or on a schedule.
 *
 * <p>A Lambda deployment does not run the local reader's startup scan. When the stream mapping is
 * disabled longer than the retention window, or a failed record can no longer be read from the
 * stream, this worker rebuilds the lag from the journal. The position guard makes a repeated run
 * harmless.
 */
public final class ResyncWorker {
  private final Resyncer resyncer;

  /** Lambda instantiates the handler class with a no-argument constructor. */
  public ResyncWorker() {
    this(LambdaRmuDependencies.create().resyncer());
  }

  ResyncWorker(Resyncer resyncer) {
    this.resyncer = resyncer;
  }

  public Map<String, Object> handleRequest(Object input, Context context) {
    var result = resyncer.resync();
    var response = new LinkedHashMap<String, Object>();
    response.put("heads", result.heads());
    response.put("skipped", result.skipped());
    response.put("advanced", result.advanced());
    response.put("failed", result.failed());
    return response;
  }
}
