# Read model updater recovery

## Goal

The read model updater (RMU) follows the head table's DynamoDB Streams and updates the MySQL read model. This document describes retrying a failed event, parking it in a durable recovery task (DLQ), and resynchronising after the stream retention window. The rules come from ADR-0002, `event-store-adapter/docs/spec/storage/dynamodb.ja.md` (DY-12 to DY-15), and the hub's `docs/acceptance-criteria.md`.

## Retry

One event is retried with both an attempt limit and a wall-clock limit.

| Setting | Default | Spring property |
|:--|:--|:--|
| Maximum attempts | 5 | `app.stream.retry-max-attempts` |
| Maximum elapsed time | 30 s | `app.stream.retry-max-elapsed-millis` |
| Initial delay | 200 ms | `app.stream.retry-initial-delay-millis` |
| Maximum delay | 5 s | `app.stream.retry-max-delay-millis` |

The delay doubles per attempt, is capped, and is spread by full jitter. The stream reader's idle delay follows the same idea from 500 ms to 30 s (`app.stream.poll-backoff-*`).

Failures fall into two groups.

- **Permanent**: an event payload that cannot be deserialised, an unsupported event type, a journal gap, or a read model constraint violation (duplicate key, foreign key, value too long). Retrying the same event cannot fix it.
- **Transient**: a database outage, lock wait, throttling, or network error. A retry may succeed.

A permanent failure is recorded as a recovery task immediately. A transient failure is retried until the attempt or elapsed limit, then recorded. If recording the task fails, the failure is returned to the caller: losing the task would hide an event that cannot be applied.

## DLQ recovery tasks

A failed event is stored in `read_model_recovery_tasks` by identifier only; the event body is never stored.

| Column | Meaning |
|:--|:--|
| `store_id` | The store identifier shared by the three tables (from `__config__`) |
| `projection_name` / `projection_version` | The projection being built (`group-chat-read-model` / `1`) |
| `aggregate_id` / `seq_nr` | The aggregate and position of the failed event |
| `failure_type` | `event_payload`, `journal_gap`, `read_model_constraint`, and so on |
| `error_message` | The failure message |
| `status` | `pending` or `completed` |

The primary key is `(store_id, projection_name, aggregate_id, seq_nr)`, so a redelivered record updates the pending task instead of creating a duplicate.

SQS is only used to wake a recovery worker; the event body is always re-read from the journal. This lets recovery succeed when the head payload is damaged but the journal entry is intact. The Lambda `OnFailure` destination stays as the safety net for a failure to record the task itself.

Recovery replays a task in this order.

1. Read the aggregate's current position.
2. If the position is below the task's `seq_nr`, read the journal in ascending order and apply it with the normal position guard.
3. Confirm that the position reached the task's `seq_nr`.
4. Mark the task `completed`.

Reading the position first makes a second run of the same task a no-op. The local reader replays pending tasks at startup and once a minute.

## Resync

Following DY-15, the RMU scans the whole head table with a strongly consistent read (`ConsistentRead = true`) and skips `__config__` when:

- the reader starts, including the first start with no positions;
- a `TrimmedDataAccessException` is raised;
- the last processed position is older than the 24-hour stream retention window.

The scan compares each head's `seq_nr` with the projection position and fills the laggards from the journal using the same guard as the stream path. Events that arrive from both the scan and the stream are deduplicated by the position guard.

`ExpiredIteratorException` only means the 15-minute shard iterator expired; it does not mean records were lost. The reader obtains a new iterator after the last processed sequence number (`AFTER_SEQUENCE_NUMBER`) instead of running a full resync.

## Transactions and idempotency

- One event, or a configured bounded range, is committed in one transaction (`app.stream.max-events-per-transaction`, default 1).
- The read model change and the `read_model_positions` advance commit together.
- Only the event at `position + 1` is applied. The position row is locked with `SELECT ... FOR UPDATE`, which serialises concurrent readers.
- A missing position row is created in one statement with `INSERT ... ON DUPLICATE KEY UPDATE`. `INSERT IGNORE` followed by a lock upgrade can deadlock when two readers create the same row.
- MySQL deadlock (1213) and lock wait timeout (1205) retry the whole transaction. The failed transaction has rolled back, so the retry cannot apply an event twice.
- A journal gap fails without advancing the position. An aggregate with an unapplied event never moves past it.

The position guard and the primary keys make applying the same event any number of times produce the same result.

## Lambda settings

For an AWS Lambda deployment, configure the event source mapping as follows. This repository has no Lambda deployment definition, so the settings are recorded here.

- `BisectBatchOnFunctionError = true`
- `FunctionResponseTypes = [ReportBatchItemFailures]`
- `MaximumRetryAttempts` set to the desired count (for example 3); failures that exhaust it go to the `OnFailure` destination.

The handler is `ReadModelUpdater#handleRequest`. For DynamoDB Streams, the partial batch response's `itemIdentifier` is the record's sequence number. A failure that was recorded as a recovery task is not redelivered; only a failure to record the task is. While an aggregate has an earlier failed record, its later records are also redelivered, because the position rule forbids advancing past the unapplied event.

## Local configuration

Defaults live under `app.stream` in `application-common.yml`; Docker Compose can override them with environment variables.

```yaml
app:
  stream:
    max-events-per-transaction: 1
    retry-max-attempts: 5
    retry-max-elapsed-millis: 30000
    retry-initial-delay-millis: 200
    retry-max-delay-millis: 5000
    poll-backoff-initial-millis: 500
    poll-backoff-max-millis: 30000
```

The recovery task table is created by `tools/refinery/migrations/V3__create_read_model_recovery_tasks.sql`.

## Tests

The `rmu` tests cover:

- dropping duplicate and stale events;
- ordering a batch by sequence number and dropping stale events;
- filling a journal gap and refusing to advance when the gap cannot be filled;
- committing earlier events and rolling back only the failed event in a batch;
- resuming from the persisted position after a restart;
- retry exhaustion, recording the recovery task, and idempotent recovery from the journal;
- a full resync after a stop longer than retention, `TrimmedDataAccessException`, and `ExpiredIteratorException`;
- concurrent application of the same record and concurrent creation of the position row;
- returning a recovery task store write failure;
- scanning the head table across multiple pages.

## Remaining work

- The Lambda event source mapping and SQS resources are not in this repository's deployment assets yet. The handler and the documented settings are in place.
- `RecoveryNotifier` is the seam for SQS. The local reader reads the durable MySQL tasks directly.
- The e2e covers the happy path and restart replay; failure injection is covered by unit tests.
