# RMU 復旧のデプロイ資産

このディレクトリには、RMU（Read Model Updater）の復旧を AWS で動かすための CloudFormation テンプレートと適用手順を置く。テンプレートは Lambda のイベントソースマッピング、OnFailure の SQS、永続復旧タスクを起動する SQS ワーカーを定義する。このリポジトリの CI はテンプレートを検証するだけで、デプロイは行わない。

## なぜ CloudFormation なのか

このリポジトリには Terraform、SAM、CDK のいずれもない。デプロイに関する資産は Docker Compose とシェルスクリプトだけで、AWS 側の構成は管理していない。そこで、追加のツールを入れずに 1 ファイルで構成を再現できる CloudFormation の JSON テンプレートを選んだ。YAML ではなく JSON にしたのは、テストから Jackson でそのまま読み、設定値を検査するためである。

## 構成

| リソース | 役割 |
|:--|:--|
| `RmuFunction` | head テーブルの Streams を購読する Lambda。ハンドラは `ReadModelUpdater::handleRequest`。 |
| `RmuEventSourceMapping` | head Streams と RMU をつなぐイベントソースマッピング。 |
| `OnFailureQueue` / `OnFailureDlq` | 再試行を使い切ったストリームレコードを受けるキューと、その DLQ。 |
| `RecoveryQueue` / `RecoveryDlq` | 復旧ワーカーを起動するメッセージのキューと、その DLQ。 |
| `RecoveryWorkerFunction` | SQS で起動し、MySQL の `read_model_recovery_tasks` を journal から再生する Lambda。ハンドラは `RecoveryWorker::handleRequest`。 |
| `RecoveryWorkerEventSourceMapping` | `RecoveryQueue` と復旧ワーカーをつなぐイベントソースマッピング。 |

## 設定の対応表

issue #625 で求められた設定と、テンプレート上の指定は次のとおり。

| 項目 | テンプレートの指定 | 値 |
|:--|:--|:--|
| `BisectBatchOnFunctionError` | `RmuEventSourceMapping.Properties.BisectBatchOnFunctionError` | `true` |
| `ReportBatchItemFailures` | `RmuEventSourceMapping.Properties.FunctionResponseTypes` | `["ReportBatchItemFailures"]` |
| OnFailure の SQS | `RmuEventSourceMapping.Properties.DestinationConfig.OnFailure.Destination` | `OnFailureQueue` の ARN |
| 再試行回数 | `RmuEventSourceMapping.Properties.MaximumRetryAttempts` | 既定 3 |
| レコードの保持期間 | `RmuEventSourceMapping.Properties.MaximumRecordAgeInSeconds` | 既定 604800 秒（7 日） |
| 関数のタイムアウト | `RmuFunction.Properties.Timeout` | 既定 300 秒 |
| 復旧キューの可視性タイムアウト | `RecoveryQueue.Properties.VisibilityTimeout` | 既定 1800 秒（ワーカーのタイムアウトの 6 倍） |
| 復旧ワーカーの起動 | `RecoveryWorkerEventSourceMapping.Properties.BatchSize` | `1` |

`BisectBatchOnFunctionError` は失敗したレコードの範囲を分割し、`ReportBatchItemFailures` は返した `itemIdentifier` のレコードだけを再配信する。両方を有効にすると、1 件の壊れたレコードで同じバッチの正常なレコードが失敗しない。

`MaximumRecordAgeInSeconds` を 7 日にしても、Streams の保持期間は 24 時間である。24 時間を超えて止まった場合は、RMU の起動時再同期が head テーブルを Scan して追いつく。`StartingPosition` は `TRIM_HORIZON` にした。位置ガードが重複を捨てるため、再配信されても結果は変わらない。

## 前提

- RMU と復旧ワーカーの Lambda 配布物（jar またはコンテナイメージ）を S3 に置いてある。両方のハンドラと依存ライブラリを含める。配布物のビルドはこのリポジトリの範囲外で、残課題に挙げている。
- head テーブルで DynamoDB Streams が有効になっている。ストリームの ARN を `HeadStreamArn` に渡す。
- MySQL のリードモデルへ到達できる。VPC 内の MySQL を使う場合は `VpcId`、`SubnetIds`、`SecurityGroupIds` を指定する。パブリックに到達できる場合は空のままでよい。
- AWS CLI と `jq` が使える。`jq` がない場合は `python3` を使う。
- デプロイ先のアカウントとリージョンに切り替えてある。

## 適用手順

`parameters.example.json` をコピーし、値を埋める。

```sh
cp deploy/parameters.example.json deploy/parameters.json
```

`CodeS3Bucket` と `CodeS3Key`、`HeadStreamArn`、`DataSourceUrl` などを実際の値にする。`VpcId` を使わない場合は空のままでよい。空の値は適用時に無視され、テンプレートの既定値が使われる。

まずテンプレートの構文だけを確認する。この段階では AWS を呼ばない。

```sh
./deploy/apply-rmu-deployment.sh --parameters deploy/parameters.json
```

問題がなければスタックを作成する。

```sh
./deploy/apply-rmu-deployment.sh --apply --stack-name cqrs-es-example-rmu --parameters deploy/parameters.json
```

スクリプトは `aws cloudformation validate-template` のあと、`aws cloudformation deploy` を `CAPABILITY_IAM` 付きで実行する。`--apply` を付けない限りデプロイしない。

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

関数のタイムアウトは 300 秒、復旧キューの可視性タイムアウトは 1800 秒である。

```sh
aws lambda get-function-configuration \
  --function-name cqrs-es-example-rmu-rmu --query 'Timeout'

aws sqs get-queue-attributes \
  --queue-url "$(aws cloudformation describe-stacks \
    --stack-name cqrs-es-example-rmu \
    --query 'Stacks[0].Outputs[?OutputKey==`RecoveryQueueUrl`].OutputValue' --output text)" \
  --attribute-names VisibilityTimeout RedrivePolicy
```

## 復旧タスクの運用手順

RMU はイベントを適用できないとき、識別子だけを MySQL の `read_model_recovery_tasks` に書き、`APP_RECOVERY_QUEUE_URL` が設定されていれば `RecoveryQueue` へ起動メッセージを送る。メッセージ本文は識別子だけで、イベント本文は含めない。復旧ワーカーはキューからメッセージを受け取ると、`recoverPending` で未処理タスクを最大 50 件処理する。本文は使わない。

ワーカーが失敗として返したメッセージは、キューの `maxReceiveCount`（既定 5）に達するまで再配信される。達したメッセージは `RecoveryDlq` に移る。

### 復旧 DLQ にメッセージが移ったとき

1. `RecoveryDlq` のメッセージ本文で、対象の集約とシーケンス番号を確認する。
2. MySQL の `read_model_recovery_tasks` で同じタスクが `pending` のまま残っていることを確認する。`error_message` と `failure_type` で原因の種類を見る。
3. 原因を取り除く。journal にイベントがあるか、リードモデルの制約違反が解消したかを確認する。
4. ワーカーをもう一度実行する。SQS のメッセージを再送するか、次のコマンドで直接起動する。ワーカーはメッセージ本文を使わないため、空のイベントでも未処理タスクを再生する。未処理タスクが残ると、このコマンドは関数エラーを返す。

```sh
aws lambda invoke \
  --function-name cqrs-es-example-rmu-rmu-recovery-worker \
  --payload '{"Records":[]}' \
  --cli-binary-format raw-in-base64-out \
  /tmp/recovery-worker-response.json
```

5. `read_model_recovery_tasks` から対象の行が消え、`read_model_positions` が対象のシーケンス番号まで進んだことを確認する。

### OnFailure キューにレコードが移ったとき

`OnFailureQueue` には、Lambda の再試行を使い切ったストリームレコードが入る。復旧タスクの記録自体に失敗した場合もここに来る。メッセージはストリームレコードの JSON なので、まず原因を特定する。記録に失敗しただけなら、同じレコードを再送すれば RMU が復旧タスクを作り直す。

### 監視

次のメトリクスを見る。

- Lambda の `Errors`、`Throttles`、`Duration`、`IteratorAge`
- `OnFailureQueue` と `RecoveryQueue` の `ApproximateNumberOfMessagesVisible`、`ApproximateAgeOfOldestMessage`
- 両方の DLQ の `ApproximateNumberOfMessagesVisible`
- `RecoveryWorkerFunction` の `Errors`

DLQ の深さが増えたら、上の復旧 DLQ の手順に進む。

## ロールバック

スタックを削除すると、テンプレートが作ったキューと Lambda も削除される。キューにメッセージが残っている場合は先に退避する。

```sh
aws cloudformation delete-stack --stack-name cqrs-es-example-rmu
```

設定だけを戻す場合は、テンプレートのパラメータを変えて `--apply` を再実行する。`RmuEventSourceMapping` の変更はスタック更新で反映される。

## テスト

`RmuDeploymentTemplateTest` がテンプレートを読み、次の点を検査する。

- `BisectBatchOnFunctionError`、`ReportBatchItemFailures`、OnFailure の SQS、`MaximumRetryAttempts` が設定されている。
- 関数のタイムアウトが再試行の最大経過時間を超え、Lambda の上限 900 秒以内である。
- 復旧キューの可視性タイムアウトがワーカーのタイムアウト以上である。
- 両方のキューに退避先がある。
- テンプレートのハンドラー名が実在するクラスとメソッドを指している。
- 環境変数の再試行設定が `RetryPolicy` の既定値と一致する。

## 残課題

- RMU と復旧ワーカーの Lambda 配布物（依存を含む jar またはコンテナイメージ）のビルドとアップロード。このリポジトリはまだ配布物を作らない。
- MySQL の認証情報はテンプレートの引数で渡している。本番では Secrets Manager か SSM Parameter Store の参照に置き換える。
- 実際の AWS 環境へのデプロイと、CloudWatch アラームの設定。
- 復旧ワーカーの同時実行数と、1 回に処理するタスク数の調整。
