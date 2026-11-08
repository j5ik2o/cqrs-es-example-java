package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class SqsRecoveryNotifierTest {

  @Test
  void sendsIdentifiersWithoutTheFailureMessage() throws Exception {
    var sent = new ArrayList<String>();
    var notifier =
        new SqsRecoveryNotifier((queueUrl, body) -> sent.add(queueUrl + "\n" + body), "queue-url");
    var task =
        new RecoveryTask(
            RmuTestSupport.STORE_ID,
            Projection.NAME,
            Projection.VERSION,
            "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z",
            7,
            FailureKind.JOURNAL_GAP,
            "journal entry is missing",
            RecoveryTask.STATUS_PENDING,
            null,
            null);

    notifier.notifyTask(task);

    assertEquals(1, sent.size());
    var body = sent.get(0).substring(sent.get(0).indexOf('\n') + 1);
    var json = new ObjectMapper().readTree(body);
    assertEquals("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z", json.path("aggregateId").asText());
    assertEquals(RmuTestSupport.STORE_ID, json.path("storeId").asText());
    assertEquals(Projection.NAME, json.path("projectionName").asText());
    assertEquals(7, json.path("seqNr").asLong());
    assertEquals("journal_gap", json.path("failureType").asText());
    assertFalse(body.contains("payload"));
    assertFalse(body.contains("journal entry is missing"));
  }
}
