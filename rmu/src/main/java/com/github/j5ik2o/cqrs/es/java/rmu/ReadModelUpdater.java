package com.github.j5ik2o.cqrs.es.java.rmu;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;

public class ReadModelUpdater {
  public void update(DynamodbEvent event) {
    event
        .getRecords()
        .forEach(
            record -> {
              var streamRecord = record.getDynamodb();
              if (streamRecord == null) {
                throw new IllegalStateException("streamRecord is null");
              }
              var attributeValues = streamRecord.getNewImage();
              if (attributeValues == null) {
                throw new IllegalStateException("attributeValues is null");
              }
              var payloadAttr = attributeValues.get("payload");
              if (payloadAttr == null || payloadAttr.isNULL()) {
                throw new IllegalStateException("Payload is missing or not a binary type");
              }
              var payloadBytes = payloadAttr.getB();
              // TODO
            });
  }
}
