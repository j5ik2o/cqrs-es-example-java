# RMU 復旧のデプロイ資産

このディレクトリには、RMU（Read Model Updater）の復旧を AWS で動かすための CloudFormation テンプレート、Lambda 配布物のビルドスクリプト、適用手順を置く。テンプレートは Lambda のイベントソースマッピング、OnFailure の SQS、永続復旧タスクを起動する SQS ワーカー、全件再同期を実行するワーカー、監視用の CloudWatch アラームを定義する。このリポジトリの CI はテンプレートと配布物を検証するだけで、実際のデプロイは行わない。

## なぜ CloudFormation なのか

このリポジトリには Terraform、SAM、CDK のいずれもない。デプロイに関する資産は Docker Compose とシェルスクリプトだけで、AWS 側の構成は管理していない。そこで、追加のツールを入れずに 1 ファイルで構成を再現できる CloudFormation の JSON テンプレートを選んだ。YAML ではなく JSON にしたのは、テストから Jackson でそのまま読み、設定値を検査するためである。

## 構成

| リソース | 役割 |
|:--|:--|
| `RmuFunction` | head テーブルの Streams を購読する Lambda。ハンドラは `ReadModelUpdater::handleRequest`。 |
| `RmuEventSourceMapping` | head Streams と RMU をつなぐイベントソースマッピング。 |
| `OnFailureQueue` / `OnFailureDlq` | 再試行を使い切ったストリームレコードを受けるキューと、その DLQ。 |
| `RecoveryQueue` / `RecoveryDlq` | 復旧ワーカーを起動するメッセージのキューと、その DLQ。 |
| `RecoveryWorkerFunction` | SQS または EventBridge で起動し、MySQL の `read_model_recovery_tasks` を journal から再生する Lambda。ハンドラは `RecoveryWorker::handleRequest`。 |
| `RecoveryWorkerEventSourceMapping` | `RecoveryQueue` と復旧ワーカーをつなぐイベントソースマッピング。 |
| `RecoveryWorkerSchedule` | 通知が届かなくても未処理タスクを処理する EventBridge の定期実行。既定は 5 分ごと。 |
| `ResyncWorkerFunction` | head テーブルを全件 Scan し、journal から遅れを埋める Lambda。ハンドラは `ResyncWorker::handleRequest`。 |
| `ResyncWorkerSchedule` | 全件再同期の EventBridge の定期実行。既定は 1 日ごと。 |
| CloudWatch アラーム | 復旧タスクの滞留、再試行上限の超過、再同期の失敗を知らせる。 |

3 つの Lambda はすべて同じ配布物を使う。ZIP のルートにハンドラのクラスを置き、依存 JAR を `lib/` に置く。これは AWS Lambda の Java ランタイムが読む配置である。

## Lambda 配布物のビルドとアップロード

`deploy/build-lambda-package.sh` が Gradle の `:rmu:lambdaZip` を実行し、`rmu/build/distributions/rmu-lambda.zip` を作る。JDK 17 か 21 で実行する。この ZIP には RMU、復旧ワーカー、再同期ワーカーの 3 ハンドラと、その依存ライブラリが入る。

```sh
./deploy/build-lambda-package.sh
```

S3 にアップロードするときは `--upload` とバケットを指定する。

```sh
./deploy/build-lambda-package.sh \
  --upload \
  --bucket my-lambda-artifacts \
  --key rmu/rmu-lambda.zip
```

スクリプトはビルド後に ZIP のパス、サイズ、SHA-256 を表示する。`--skip-build` を付けると、既存の ZIP をそのままアップロードする。`--output` で ZIP の出力先を変えられる。アップロードした S3 のバケットとキーを、テンプレートの `CodeS3Bucket` と `CodeS3Key` に渡す。

Lambda を更新するときは、新しい ZIP を別のキーにアップロードし、`CodeS3Key` を変えてスタックを更新する。同じキーを上書きすると、CloudFormation はコードの変更を検知しないため、スタックは古い配布物のままになる。

## Secrets Manager の設定

テンプレートは MySQL のユーザー名とパスワードを平文で受け取らない。`DataSourceSecretArn` に Secrets Manager のシークレット ARN を渡し、Lambda が起動時に `GetSecretValue` で取得する。シークレットの値は次の JSON にする。

```json
{
  "username": "ceer",
  "password": "change-me"
}
```

`url` キーを入れると、テンプレートの `DataSourceUrl` より優先される。`url` を入れない場合は `DataSourceUrl` を使う。シークレットは Lambda と同じリージョンに置く。

```sh
aws secretsmanager create-secret \
  --name ceer/db-credentials \
  --secret-string '{"username":"ceer","password":"change-me"}'
```

作成した ARN を `DataSourceSecretArn` に渡す。3 つの Lambda の実行ロールには、その ARN に対する `secretsmanager:GetSecretValue` を付けてある。カスタマー管理の KMS キーでシークレットを暗号化した場合は、実行ロールに `kms:Decrypt` も追加する。シークレットをローテーションした場合、起動済みの Lambda コンテナは古い値を保持する。新しい値を使うには、関数を更新するか、コンテナが入れ替わるまで待つ。

## 設定の対応表

issue #627 で求められた設定と、テンプレート上の指定は次のとおり。

| 項目 | テンプレートの指定 | 値 |
|:--|:--|:--|
| `BisectBatchOnFunctionError` | `RmuEventSourceMapping.Properties.BisectBatchOnFunctionError` | `true` |
| `ReportBatchItemFailures` | `RmuEventSourceMapping.Properties.FunctionResponseTypes` | `["ReportBatchItemFailures"]` |
| OnFailure の SQS | `RmuEventSourceMapping.Properties.DestinationConfig.OnFailure.Destination` | `OnFailureQueue` の ARN |
| 再試行回数 | `RmuEventSourceMapping.Properties.MaximumRetryAttempts` | 既定 3 |
| レコードの保持期間 | `RmuEventSourceMapping.Properties.MaximumRecordAgeInSeconds` | 既定 604800 秒（7 日） |
| RMU のタイムアウト | `RmuFunction.Properties.Timeout` | 既定 300 秒 |
| 復旧ワーカーのタイムアウト | `RecoveryWorkerFunction.Properties.Timeout` | 既定 300 秒 |
| 復旧ワーカーの同時実行数 | `RecoveryWorkerFunction.Properties.ReservedConcurrentExecutions` | 既定 1 |
| 復旧キューの可視性タイムアウト | `RecoveryQueue.Properties.VisibilityTimeout` | 既定 1800 秒（ワーカーのタイムアウトの 6 倍） |
| 復旧ワーカーの起動 | `RecoveryWorkerEventSourceMapping.Properties.BatchSize` | `1` |
| 復旧タスクの定期処理 | `RecoveryWorkerSchedule.Properties.ScheduleExpression` | 既定 `rate(5 minutes)` |
| 全件再同期 | `ResyncWorkerSchedule.Properties.ScheduleExpression` | 既定 `rate(1 day)` |
| MySQL の認証情報 | `DataSourceSecretArn` と `APP_DB_SECRET_ARN` | Secrets Manager のシークレット ARN |
| アラームの通知先 | `AlarmSnsTopicArn` | 空なら通知しない |

`BisectBatchOnFunctionError` は失敗したレコードの範囲を分割し、`ReportBatchItemFailures` は失敗したレコードを Lambda に伝える。DynamoDB Streams では、Lambda が返された失敗のうち最小のシーケンス番号をチェックポイントにして、そこから後続のレコードも再試行する。失敗したレコードより後ろの正常なレコードが再配信されることがあるが、位置ガードが重複を捨てるため結果は変わらない。

`MaximumRecordAgeInSeconds` を 7 日にしても、Streams の保持期間は 24 時間である。Lambda はローカルの reader のような起動時 Scan を行わないため、24 時間を超えて止まった場合は `ResyncWorker` の全件再同期で追いつく。`StartingPosition` は `TRIM_HORIZON` にした。位置ガードが重複を捨てるため、再配信されても結果は変わらない。

## 同時実行数とタイムアウトの推奨値

復旧ワーカーは同じ集約の復旧を並行して走らせないために、同時実行数を 1 にする。`RecoveryWorkerReservedConcurrency` の既定値は 1 で、復旧タスクのストアが同時実行に耐えると確認できた場合だけ上げる。SQS のイベントソースマッピングには `BatchSize` 1 を指定し、1 メッセージで最大 10 バッチ、1 バッチ最大 `RecoveryMaxTasksPerRun`（既定 50 件）を処理する。1 回の起動で最大 500 件まで進む。

タイムアウトは次の値にする。

- RMU は 300 秒。再試行の最大経過時間 30 秒とデータベース処理を含めて、Lambda の上限 900 秒以内に収める。
- 復旧ワーカーは 300 秒。復旧キューの可視性タイムアウトはその 6 倍の 1800 秒にする。AWS は可視性タイムアウトを関数タイムアウトの 6 倍以上にすることを勧めている。
- 再同期ワーカーは 900 秒。head を全件 Scan するため、Lambda の上限を既定にしている。

テーブルが大きい場合や、1 回の復旧で処理する件数が足りない場合は、`RecoveryMaxTasksPerRun`、`RecoveryWorkerTimeoutSeconds`、`RecoveryQueueVisibilityTimeoutSeconds` を同じ比率で見直す。可視性タイムアウトが関数タイムアウトを下回ると、同じメッセージが処理中に再配信される。

## 前提

- Lambda 配布物を `deploy/build-lambda-package.sh` で作り、S3 に置いてある。
- head テーブルで DynamoDB Streams が有効になっている。ストリームの ARN を `HeadStreamArn` に渡す。
- MySQL のリードモデルへ到達できる。VPC 内の MySQL を使う場合は `VpcId`、`SubnetIds`、`SecurityGroupIds` を指定する。パブリックに到達できる場合は空のままでよい。
- Secrets Manager に認証情報のシークレットがある。
- AWS CLI が使える。パラメータファイルの構文確認には `jq` か `python3` を使う。
- デプロイ先のアカウントとリージョンに切り替えてある。

## 適用手順

配布物を作り、S3 にアップロードする。

```sh
./deploy/build-lambda-package.sh --upload --bucket my-lambda-artifacts --key rmu/rmu-lambda.zip
```

Secrets Manager に認証情報を置き、ARN を控える。手順は「Secrets Manager の設定」にある。

`parameters.example.json` をコピーし、値を埋める。

```sh
cp deploy/parameters.example.json deploy/parameters.json
```

`CodeS3Bucket` と `CodeS3Key`、`HeadStreamArn`、`DataSourceUrl`、`DataSourceSecretArn` を実際の値にする。`VpcId` を使わない場合は空のままでよい。パラメータファイルは `ParameterKey` と `ParameterValue` の組を並べた JSON 配列で、AWS CLI の `--parameter-overrides file://...` がそのまま読む。空の値も明示的に渡るため、既存スタックの VPC 設定を空にして外すこともできる。

まずテンプレートの構文だけを確認する。この段階では AWS を呼ばない。

```sh
./deploy/apply-rmu-deployment.sh --parameters deploy/parameters.json
```

問題がなければスタックを作成する。

```sh
./deploy/apply-rmu-deployment.sh --apply --stack-name cqrs-es-example-rmu --parameters deploy/parameters.json
```

スクリプトは `aws cloudformation validate-template` のあと、`aws cloudformation deploy` を `CAPABILITY_IAM` 付きで実行する。`--apply` を付けない限りデプロイしない。

スタック作成後、`ResyncWorkerFunction` を 1 回実行して head の全件再同期を済ませる。

```sh
aws lambda invoke \
  --function-name cqrs-es-example-rmu-rmu-resync-worker \
  --payload '{}' \
  --cli-binary-format raw-in-base64-out \
  /tmp/resync-worker-response.json
```

`/tmp/resync-worker-response.json` には `heads`、`skipped`、`advanced`、`failed` の件数が入る。

## 適用後の確認

イベントソースマッピングの設定を確認する。

```sh
aws lambda get-event-source-mapping \
  --uuid "$(aws cloudformation describe-stack-resource \
    --stack-name cqrs-es-example-rmu \
    --logical-resource-id RmuEventSourceMapping \
    --query 'StackResourceDetail.PhysicalResourceId' --output text)" \
  --query '{State:State,Bisect:BisectBatchOnFunctionError,ResponseTypes:FunctionResponseTypes,Retries:MaximumRetryAttempts,OnFailure:DestinationConfig.OnFailure.Destination}'
```

次の値になっていることを確かめる。

- `BisectBatchOnFunctionError` が `true`
- `FunctionResponseTypes` に `ReportBatchItemFailures` が含まれる
- `DestinationConfig.OnFailure.Destination` が `OnFailureQueue` の ARN
- `State` が `Enabled`

関数のタイムアウトと同時実行数を確認する。

```sh
aws lambda get-function-configuration \
  --function-name cqrs-es-example-rmu-rmu --query 'Timeout'

aws lambda get-function-configuration \
  --function-name cqrs-es-example-rmu-rmu-recovery-worker \
  --query '{Timeout:Timeout,ReservedConcurrentExecutions:ReservedConcurrentExecutions}'
```

復旧キューの可視性タイムアウトと退避先を確認する。

```sh
aws sqs get-queue-attributes \
  --queue-url "$(aws cloudformation describe-stacks \
    --stack-name cqrs-es-example-rmu \
    --query 'Stacks[0].Outputs[?OutputKey==`RecoveryQueueUrl`].OutputValue' --output text)" \
  --attribute-names VisibilityTimeout RedrivePolicy
```

EventBridge の定期実行を確認する。

```sh
aws cloudformation describe-stack-resources --stack-name cqrs-es-example-rmu \
  --query 'StackResources[?ResourceType==`AWS::Events::Rule`].[LogicalResourceId,PhysicalResourceId]' \
  --output table
```

`RecoveryWorkerSchedule` は既定で 5 分ごと、`ResyncWorkerSchedule` は既定で 1 日ごとに動く。

アラームが作られたことを確認する。

```sh
aws cloudwatch describe-alarms \
  --alarm-name-prefix cqrs-es-example-rmu
```

`AlarmSnsTopicArn` を設定した場合は、`AlarmActions` にそのトピックが入っていることも確かめる。

## 復旧タスクの運用手順

RMU はイベントを適用できないとき、識別子だけを MySQL の `read_model_recovery_tasks` に書き、`APP_RECOVERY_QUEUE_URL` が設定されていれば `RecoveryQueue` へ起動メッセージを送る。メッセージ本文は識別子だけで、イベント本文は含めない。復旧ワーカーは起動のたびに、未処理タスクを `RecoveryMaxTasksPerRun`（既定 50 件）ずつ最大 10 バッチ処理する。50 件を超える滞留も 1 回の起動で進み、それでも残るときは SQS が再配信する。EventBridge の定期実行（既定 5 分ごと）があるため、通知が届かなくても未処理タスクは処理される。

ワーカーが失敗として返したメッセージは、キューの `maxReceiveCount`（既定 5）に達するまで再配信される。達したメッセージは `RecoveryDlq` に移る。

### 復旧 DLQ にメッセージが移ったとき

1. `RecoveryDlq` のメッセージ本文で、通知された集約とシーケンス番号を確認する。通知は起動のきっかけであり、そのタスクがまだ `pending` とは限らない。復旧が完了したタスクの通知が残っていることもある。
2. MySQL の `read_model_recovery_tasks` で、`store_id` と投影（`group-chat-read-model` / `1`）に該当する `status = 'pending'` の行を確認する。それぞれの `error_message` と `failure_type` で原因の種類を見る。
3. 原因を取り除く。journal にイベントがあるか、リードモデルの制約違反が解消したかを確認する。
4. ワーカーをもう一度実行する。SQS のメッセージを再送するか、次のコマンドで直接起動する。ワーカーはメッセージ本文を使わないため、空のイベントでも未処理タスクを再生する。未処理タスクが残ると、このコマンドは関数エラーを返す。

```sh
aws lambda invoke \
  --function-name cqrs-es-example-rmu-rmu-recovery-worker \
  --payload '{"Records":[]}' \
  --cli-binary-format raw-in-base64-out \
  /tmp/recovery-worker-response.json
```

5. `read_model_recovery_tasks` の対象行の `status` が `completed` になり、`read_model_positions` が対象のシーケンス番号以上に進んだことを確認する。

### OnFailure キューにメッセージが移ったとき

`OnFailureQueue` には、Lambda の再試行を使い切ったバッチのメタデータが入る。本文は元のレコードではなく、`requestContext` と `DynamoDBBatchInfo`（`shardId`、`startSequenceNumber`、`endSequenceNumber`、`streamArn` など）を含む JSON である。この情報だけでは復旧タスクを作れないため、次のどちらかを行う。

- ストリームの保持期間内であれば、`DynamoDBBatchInfo` のシーケンス番号の範囲を `GetRecords` で読み直し、RMU の入力と同じ形に組み立てて再送する。
- 保持期間を過ぎている場合は `ResyncWorker` を実行し、head と journal から全件再同期する。

復旧タスクの記録自体に失敗した場合もここに来る。その場合は `ResyncWorker` か、同じレコードの再送で復旧タスクを作り直す。

### 監視

テンプレートは次のアラームを作る。通知先は `AlarmSnsTopicArn` を設定したときだけ有効になる。

| アラーム | メトリクス | 意味 |
|:--|:--|:--|
| `RecoveryBacklogAlarm` | `AWS/SQS` の `RecoveryQueue` の `ApproximateNumberOfMessagesVisible` | 復旧キューの滞留。5 分間隔で 2 回、しきい値（既定 10）以上になったら通知する。 |
| `RecoveryWorkerErrorsAlarm` | `AWS/Lambda` の `RecoveryWorkerFunction` の `Errors` | 復旧ワーカーがエラーを返した。 |
| `OnFailureQueueAlarm` | `AWS/SQS` の `OnFailureQueue` の可視メッセージ数 | ストリームレコードが再試行上限に達した。 |
| `OnFailureDlqAlarm` | `AWS/SQS` の `OnFailureDlq` の可視メッセージ数 | OnFailure キューでも処理できなかったメッセージがある。 |
| `ResyncWorkerErrorsAlarm` | `AWS/Lambda` の `ResyncWorkerFunction` の `Errors` | 全件再同期が失敗した。 |
| `ResyncScheduleFailedInvocationsAlarm` | `AWS/Events` の `ResyncWorkerSchedule` の `FailedInvocations` | 定期実行が再同期ワーカーを起動できなかった。 |

復旧タスクの滞留は、キューの深さだけでなく MySQL の `read_model_recovery_tasks` の `pending` 件数でも確認する。通知が届かない場合、キューにはメッセージが残らないが、タスクは MySQL に残る。定期実行がそのタスクを処理するため、キューのアラームは初期の兆候として扱う。

あわせて次のメトリクスを見る。

- Lambda の `Errors`、`Throttles`、`Duration`、`IteratorAge`
- `OnFailureQueue` と `RecoveryQueue` の `ApproximateNumberOfMessagesVisible`、`ApproximateAgeOfOldestMessage`
- 両方の DLQ の `ApproximateNumberOfMessagesVisible`
- `RecoveryWorkerSchedule` と `ResyncWorkerSchedule` の `Invocations`、`FailedInvocations`

DLQ の深さが増えたら、上の復旧 DLQ の手順に進む。

## ロールバック

スタックを削除すると、テンプレートが作ったキュー、Lambda、アラームも削除される。キューにメッセージが残っている場合は先に退避する。

```sh
aws cloudformation delete-stack --stack-name cqrs-es-example-rmu
```

設定だけを戻す場合は、テンプレートのパラメータを変えて `--apply` を再実行する。`RmuEventSourceMapping` の変更はスタック更新で反映される。Lambda のコードを戻す場合は、以前の ZIP の S3 キーを `CodeS3Key` に指定して更新する。同じキーを上書きした場合は CloudFormation が変更を検知しないため、新しいキーを用意する。

アラームだけを止める場合は、`AlarmSnsTopicArn` を空にして更新する。アラームのリソースは残るが、通知は送られなくなる。

## テスト

`RmuDeploymentTemplateTest` がテンプレートを読み、次の点を検査する。

- `BisectBatchOnFunctionError`、`ReportBatchItemFailures`、OnFailure の SQS、`MaximumRetryAttempts` が設定されている。
- 関数のタイムアウトが再試行の最大経過時間を超え、Lambda の上限 900 秒以内である。
- 復旧キューの可視性タイムアウトがワーカーのタイムアウトの 6 倍である。
- 復旧ワーカーの同時実行数が 1 以上で、関数に設定されている。
- 3 つの Lambda が `APP_DB_SECRET_ARN` を受け取り、平文のパスワードを環境変数に持たない。
- 3 つの実行ロールがシークレットの読み取りを許可している。
- 両方のキューに退避先がある。
- テンプレートのハンドラ名が実在するクラスとメソッドを指している。
- 環境変数の再試行設定が `RetryPolicy` の既定値と一致する。
- 復旧と再同期の定期実行が有効で、それぞれのワーカーを呼び出している。
- 復旧タスクの 1 回あたりの上限が正の値である。
- 復旧の滞留、再試行上限の超過、再同期の失敗に対するアラームがある。
- アラームの通知先は `AlarmSnsTopicArn` を設定したときだけ使う。

`DatabaseSettingsTest` は、Secrets Manager の JSON から接続設定を取り出すこと、URL のフォールバック、必須キーの欠落を確認する。

`ResyncWorkerTest` は、再同期ワーカーが `heads`、`skipped`、`advanced`、`failed` の件数を返すことを確認する。

CI の `Validate deployment assets` ジョブは、`cfn-lint` でテンプレートを検査し、シェルスクリプトの構文を確認し、`:rmu:lambdaZip` を実行して ZIP に 3 つのハンドラと依存 JAR が入っていることを確かめる。`cfn-lint` の W1030 は、VPC を使わないときの空の `SubnetIds` と `SecurityGroupIds` に対する誤検出であるため、`.cfnlintrc` で除外している。

## 残課題

- 実際の AWS 環境へのデプロイと、デプロイ後の疎通確認。このリポジトリは配布物とテンプレートを作るところまでで、デプロイは行わない。
- 復旧タスクの `pending` 件数を CloudWatch のカスタムメトリクスにして、キューの深さに依存しないアラームを追加する。
- 全件再同期の頻度（既定は 1 日 1 回）。テーブルが大きい場合は `ResyncScheduleExpression` を調整する。
- 配布物のサイズと Lambda のコールドスタート。依存 JAR を減らすか、レイヤーに分ける余地がある。
- シークレットのローテーション時に、起動済みのコンテナが新しい認証情報を読む仕組み。現状は関数の更新かコンテナの入れ替えで反映する。
