# Read Model Updater の復旧

## 目的

Read Model Updater（RMU）は head テーブルの Streams を購読して MySQL のリードモデルを更新する。この文書は、イベントの適用に失敗したときの再試行、永続的な復旧タスク（DLQ）、および Streams の保持期間を超えたときの再同期の動きを説明する。根拠は ADR-0002、`event-store-adapter` の `docs/spec/storage/dynamodb.ja.md`（DY-12〜15）、ハブの `docs/acceptance-criteria.md` である。

## 再試行

1 件のイベントは、回数と経過時間の両方で上限を設けて再試行する。

| 設定 | 既定値 | 環境変数（Spring） |
|:--|:--|:--|
| 最大試行回数 | 5 | `APP_STREAM_RETRY_MAX_ATTEMPTS` |
| 最大経過時間 | 30 秒 | `APP_STREAM_RETRY_MAX_ELAPSED_MILLIS` |
| 初回待ち時間 | 200 ミリ秒 | `APP_STREAM_RETRY_INITIAL_DELAY_MILLIS` |
| 待ち時間の上限 | 5 秒 | `APP_STREAM_RETRY_MAX_DELAY_MILLIS` |

待ち時間は試行ごとに倍にし、上限で頭打ちにしたあと、full jitter で 0 からその値までに散らす。レプリカや再起動した reader が同じタイミングで叩き続けないようにするためである。Streams の読み取りが空だったときの待ち時間も、同じ考え方で 500 ミリ秒から 30 秒まで増やす（`APP_STREAM_POLL_BACKOFF_*`）。

失敗は 2 種類に分ける。

- **恒久的な失敗**: payload の復元失敗、未対応のイベント型、journal の欠番、リードモデルの制約違反（重複キー、外部キー違反、値の長さ超過など）。同じイベントを再試行しても直らない。
- **一時的な失敗**: DB の接続断、ロック待ち、スロットリング、ネットワークエラーなど。再試行で直る可能性がある。

恒久的な失敗は再試行せず、すぐに復旧タスクへ記録する。一時的な失敗は上限まで再試行し、上限に達したら同じく復旧タスクへ記録する。記録に失敗した場合は、その失敗を呼び出し元へ返す。復旧タスクを失うと、直らないイベントを見落とすためである。

1 回の SQL 文は、再試行の最大経過時間と同じ値を MyBatis の `defaultStatementTimeout` として設定する。MySQL の行ロック待ちは既定で最大 50 秒続くため、文の実行そのものにも上限を設けないと、次の試行の前に経過時間の上限を超えてしまう。

## DLQ（復旧タスク）

失敗したイベントは、イベント本文を持たず、識別子だけを `read_model_recovery_tasks` に保存する。

| 列 | 内容 |
|:--|:--|
| `store_id` | 3 テーブル共通のストア識別子（`__config__` の `store_id`） |
| `projection_name` / `projection_version` | 投影の名前と版（`group-chat-read-model` / `1`） |
| `aggregate_id` / `seq_nr` | 失敗したイベントの集約と位置 |
| `failure_type` | 失敗の種類（`event_payload`、`journal_gap`、`read_model_constraint` など） |
| `error_message` | 原因のメッセージ |
| `status` | `pending` または `completed` |

主キーは `(store_id, projection_name, aggregate_id, seq_nr)` である。同じレコードが再配信されても、既存の `pending` 行を更新するだけで重複しない。

SQS を使う場合は、復旧ワーカーを起こすためだけに使い、イベント本文は運ばない。復旧処理は必ず journal から本文を読み直す。これにより、head の payload が壊れていても journal が正しければ復旧できる。Lambda の `OnFailure` 通知は、復旧タスクの記録自体に失敗した場合の安全網として残す。

復旧は次の順で行う。

1. 集約の現在位置を読み直す。
2. 現在位置が対象の `seq_nr` より小さければ、journal を昇順に読み、通常の位置ガードを使って適用する。
3. 適用後の位置が対象の `seq_nr` 以上になったことを確認する。
4. 復旧タスクを `completed` にする。

位置を先に読み直すため、同じタスクを 2 回実行しても結果は変わらない。ローカルの reader は起動時と 1 分ごとに `pending` のタスクを処理する。

復旧処理は 1 回に最大 50 件を対象にする。失敗したタスクは `updated_at` を更新して待ち行列の後ろへ移す。これにより、大量の未処理タスクがストリームのポーリングを止め続けたり、同じ失敗タスクだけが毎回先頭に来たりしない。

## 再同期

DY-15 に従い、次の場合に head テーブルを強整合読み取り（`ConsistentRead = true`）で全件 Scan する。`__config__` は除外する。

- reader の起動時（位置がまったくない初回を含む）
- `TrimmedDataAccessException` を受けたとき
- 最後に処理した位置が Streams の保持期間（24 時間）より古いとき

Scan では各ヘッドの `seq_nr` と投影の位置を比べ、位置が小さい集約だけ journal から補う。補う処理は通常の適用と同じ位置ガードを使う。Scan と Streams の両方から届いたイベントは、位置ガードで重複を捨てる。

起動時の Scan が完了するまでストリームの購読は始めない。Scan が失敗したときはバックオフして、完了するまで再同期をやり直す。走査できなかった集約を、復旧タスクがないまま見落とさないためである。

`ExpiredIteratorException` は、シャードイテレーターの有効期限（15 分）が切れただけであり、レコードが消えたことを意味しない。この場合は全件再同期をせず、最後に処理したシーケンス番号の後から（`AFTER_SEQUENCE_NUMBER`）イテレーターを取り直す。

## トランザクションと冪等性

- 1 イベント、または設定した上限までの範囲を 1 トランザクションで確定する（`APP_STREAM_MAX_EVENTS_PER_TRANSACTION`、既定値 1）。
- リードモデルの更新と `read_model_positions` の前進を同じトランザクションに入れる。
- 適用するのは厳密に「現在位置 + 1」のイベントだけである。位置行は `SELECT ... FOR UPDATE` でロックし、同時に適用しようとした reader を直列化する。
- 位置行がまだない場合は、`INSERT ... ON DUPLICATE KEY UPDATE` で 1 文のうちに作る。`INSERT IGNORE` のあとにロックを取ると、同じ位置行を同時に作る 2 つのトランザクションがデッドロックすることがあるためである。
- デッドロック（MySQL 1213）とロック待ちタイムアウト（1205）は、トランザクションを丸ごとやり直す。失敗したトランザクションはロールバック済みなので、やり直しても二重適用にはならない。
- journal に欠番があれば、位置を進めずに失敗とする。未適用のイベントがある集約は、その位置より先へ進めない。

`openSession(false)` だけでは、MyBatis Spring の `SpringManagedTransaction` は実際のトランザクションを開始しない。そのため、位置行のロックからコミットまでを JDBC の接続で明示的に囲む。これで Spring 管理の接続でも、リードモデルと位置が同じトランザクションで確定する。

同じイベントを何度適用しても結果が変わらないことは、位置ガードと主キーで担保する。

## Lambda の設定

Lambda で動かす場合は、イベントソースマッピングに次を設定する。このリポジトリには Lambda のデプロイ定義がないため、設定値はここに記録する。

- `BisectBatchOnFunctionError = true`: 失敗したレコードの範囲を分割して再試行する。
- `FunctionResponseTypes = [ReportBatchItemFailures]`: 失敗したレコードだけを再配信する。
- `MaximumRetryAttempts`: 必要な回数（例: 3）を設定し、それでも失敗したレコードは `OnFailure` の送信先へ流す。

ハンドラは `com.github.j5ik2o.cqrs.es.java.rmu.ReadModelUpdater::handleRequest` である。引数なしのコンストラクターが環境変数から依存関係を組み立てるため、Lambda はハンドラを直接生成できる。必要な環境変数は `APP_STREAM_HEAD_TABLE_NAME`、`APP_STREAM_JOURNAL_TABLE_NAME`、`APP_STREAM_MAX_EVENTS_PER_TRANSACTION`、`APP_STREAM_RETRY_*`、`APP_DYNAMO_DB_*`（または `AWS_REGION`）、`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` である。MySQL の JDBC ドライバーを Lambda のクラスパスに入れること。

部分バッチ応答の `itemIdentifier` には、DynamoDB Streams の仕様に合わせてレコードのシーケンス番号を入れる。復旧タスクに記録できた失敗は再配信しない。記録に失敗した場合だけ再配信する。同じ集約で先に失敗したレコードがある間は、後続のレコードも再配信対象にする。位置より先へ進めないためである。

## ローカルの設定

`application-common.yml` の `app.stream` に既定値を置いている。Docker Compose では環境変数で上書きする。

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

復旧タスクの表は `tools/refinery/migrations/V3__create_read_model_recovery_tasks.sql` で作る。

## テスト

`rmu` のテストは次の動きを確認する。

- 重複したイベントと古いイベントを捨てる。
- 逆順のイベントをシーケンス番号順に並べ、古いものは捨てる。
- journal の欠番を補完し、補完できなければ位置を進めない。
- バッチの途中で失敗したとき、成功したイベントだけを確定する。
- 再起動後、保存済みの位置から再開する。
- 再試行の上限に達したら復旧タスクを記録し、journal から冪等に復旧する。
- 24 時間を超えて止まった場合の全件再同期、`TrimmedDataAccessException`、`ExpiredIteratorException` を扱う。
- 同じレコードの同時適用と、位置行の同時作成。
- 復旧タスクの書き込みに失敗したとき、その失敗を返す。
- ヘッドの Scan が複数ページにまたがる。

## 残課題

- Lambda のイベントソースマッピングと SQS の実体は、このリポジトリのデプロイ資産にまだない。設定値とハンドラは実装済みである。
- SQS への通知は `RecoveryNotifier` の差し込み口だけを用意している。ローカルの reader は DB の復旧タスクを直接読む。
- e2e は正常系と再起動の再生を確認する。失敗の注入はユニットテストで確認している。
