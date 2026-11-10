# ローカル3アプリのポートと環境変数

[English](LOCAL_CONFIGURATION.md)

通常のDocker Composeによるローカル実行向けの設定リファレンスです。値はmainの `30f1cbc464734f4abf23b0f80e5c92757ae48ff9` と照合しています。

## アプリとポート

| アプリと起動引数 | 役割 | ホストポート | コンテナ内部ポート | ホストからのGraphQL URL |
| --- | --- | --- | --- | --- |
| Write `-w` | Mutationでイベントを書込む | 58080 | 8080 | `http://localhost:58080/graphql` |
| RMU `-l` | head StreamsとjournalからMySQLのRead Modelを更新 | 58081 | 8080 | 利用者向けGraphQL APIなし |
| Read `-r` | MySQLのRead ModelをQueryで取得 | 58082 | 8080 | `http://localhost:58082/graphql` |

RMUのポート公開はComposeの宣言です。RMUはバックグラウンド処理で、利用者はWriteとReadにアクセスします。Read Modelの更新は非同期です。コンテナ間のAPI URLは `http://write-api-server-1:8080/graphql` と `http://read-api-server-1:8080/graphql` です。

e2e専用Composeは各サービスの `ports` を解除するため、上記ホストポートは公開しません。テストコンテナは内部URLを使用します。

## アプリ別の環境変数

Composeは下表の値をコンテナに設定します。ホストのシェルで同名変数をexportする必要はありません。既定値は `application-common.yml` の値です。環境変数による上書きは任意ですが、通常Composeと同じ構成を手動で起動する場合、ポート、LocalStack接続先、DB接続情報の変更が必要です。

| 環境変数 | 使用アプリ | Compose値 | ファイルの既定値 | 意味 |
| --- | --- | --- | --- | --- |
| `SERVER_ADDRESS` | Write, RMU, Read | `0.0.0.0` | `未指定 / unset` | 待受アドレス。Composeでは全インターフェース。 |
| `SERVER_PORT` | Write, RMU, Read | `8080` | `48080` | アプリの待受ポート。ホスト公開ポートとは別。 |
| `APP_PERSISTENCE_JOURNAL_TABLE_NAME` | Write | `journal` | `journal` | イベントを保存するテーブル。 |
| `APP_PERSISTENCE_SNAPSHOT_TABLE_NAME` | Write | `snapshot` | `snapshot` | スナップショットを保存するテーブル。 |
| `APP_PERSISTENCE_HEAD_TABLE_NAME` | Write | `head` | `head` | 集約の最新位置を保存するテーブル。 |
| `APP_PERSISTENCE_SNAPSHOT_AID_INDEX_NAME` | Write | `snapshot-aid-index` | `snapshot-aid-index` | snapshotのGSI名。テーブル作成側と一致させる。 |
| `APP_PERSISTENCE_SNAPSHOT_INTERVAL` | Write | `10` | `10` | スナップショット作成間隔のイベント数。 |
| `APP_DYNAMO_DB_REGION` | Write, RMU | `ap-northeast-1` | `ap-northeast-1` | DynamoDBとStreamsのリージョン。 |
| `APP_DYNAMO_DB_ENDPOINT_URL` | Write, RMU | `http://localstack:4566` | `http://localhost:8000` | AWS接続先。ComposeではLocalStack。 |
| `APP_DYNAMO_DB_ACCESS_KEY_ID` | Write, RMU | `x` | `設定なし / none` | Composeの変数名。下記のaccess-keyとの相違を参照。 |
| `APP_DYNAMO_DB_SECRET_ACCESS_KEY` | Write, RMU | `x` | `x` | ローカル用ダミーシークレット。 |
| `APP_STREAM_HEAD_TABLE_NAME` | RMU | `head` | `head` | Streams購読と起動時の照合に使うheadテーブル。 |
| `APP_STREAM_JOURNAL_TABLE_NAME` | RMU | `journal` | `journal` | 欠番を補うイベントの読取り元。 |
| `APP_STREAM_MAX_ITEM_COUNT` | RMU | `32` | `32` | Streamsの1回の取得件数上限。 |
| `SPRING_DATASOURCE_URL` | RMU, Read | `jdbc:mysql://mysql-local:3306/ceer` | `jdbc:mysql://localhost:3306/test` | Read Model DBのJDBC URL。 |
| `SPRING_DATASOURCE_USERNAME` | RMU, Read | `ceer` | `sa` | Read Model DBのユーザー名。 |
| `SPRING_DATASOURCE_PASSWORD` | RMU, Read | `ceer` | `空 / empty` | Read Model DBのパスワード。 |

Writeは `APP_PERSISTENCE_*` のテーブル名とsnapshot GSIがDB作成設定に一致する必要があります。RMUの `APP_STREAM_HEAD_TABLE_NAME` と `APP_STREAM_JOURNAL_TABLE_NAME` はWriteのテーブルを指します。RMUとReadは同じMySQL DBとマイグレーション済みスキーマを必要とします。Writeのイベント保存先はDynamoDBです。

`SERVER_ADDRESS` はファイルに既定値がなく、未指定時はサーバーの標準バインド動作に従います。3プロセスをホストで直接実行すると、既定の `48080` が競合するため、各プロセスに異なる `SERVER_PORT` が必要です。ホスト上ではLocalStackに `http://localhost:4566`、MySQLに `jdbc:mysql://localhost:3306/ceer` を使います。通常ComposeはDBの `4566` と `3306` も公開します。

### AWS認証キー名の相違

Composeは `APP_DYNAMO_DB_ACCESS_KEY_ID=x` を設定しますが、実装が参照するプロパティは `app.dynamo-db.access-key` です。`access-key-id` への明示的な対応付けはありません。ローカル実行では共通設定の `access-key: x` が存在します。認証情報を変える場合は `access-key` 自体を上書きする必要があり、`ACCESS_KEY_ID` の変更だけに依存できません。

`AWS_REGION=ap-northeast-1` も全3アプリに設定されます。DynamoDBクライアントの明示設定には `APP_DYNAMO_DB_REGION` を使います。共通設定にリージョン、接続先、キーが揃っているため、ローカル構成は明示的な接続先と固定認証情報を使用します。

### RMUの任意設定

以下はComposeで未指定の任意設定です。共通設定と `AppConfig.Stream` に同じ既定値があります。詳細は[RMU復旧](RMU_RECOVERY.ja.md)を参照してください。

| 環境変数 | 既定値 | 意味 |
| --- | --- | --- |
| `APP_STREAM_MAX_EVENTS_PER_TRANSACTION` | `1` | 1トランザクションの最大イベント数 |
| `APP_STREAM_RETRY_MAX_ATTEMPTS` | `5` | 最大試行回数 |
| `APP_STREAM_RETRY_MAX_ELAPSED_MILLIS` | `30000` | 再試行の時間上限、ミリ秒 |
| `APP_STREAM_RETRY_INITIAL_DELAY_MILLIS` | `200` | 再試行の初期待機、ミリ秒 |
| `APP_STREAM_RETRY_MAX_DELAY_MILLIS` | `5000` | 再試行の最大待機、ミリ秒 |
| `APP_STREAM_POLL_BACKOFF_INITIAL_MILLIS` | `500` | ポーリングの初期待機、ミリ秒 |
| `APP_STREAM_POLL_BACKOFF_MAX_MILLIS` | `30000` | ポーリングの最大待機、ミリ秒 |

## 設定の参照元

- [Application Compose](../tools/docker-compose/docker-compose-applications.yml)
- [Database Compose](../tools/docker-compose/docker-compose-databases.yml)
- [e2e Compose](../tools/docker-compose/docker-compose-e2e-test.yml)
- [Common defaults](../bootstrap/src/main/resources/application-common.yml)
- [AppConfig](../bootstrap/src/main/java/com/github/j5ik2o/cqrs/es/java/main/AppConfig.java)
- [Write repository configuration](../command/interface-adaptor-impl/src/main/java/com/github/j5ik2o/cqrs/es/java/command/interface_adaptor/repository/RepositoryConfig.java)
- [DynamoDB clients](../bootstrap/src/main/java/com/github/j5ik2o/cqrs/es/java/main/DynamoDbConfig.java)
- [RMU configuration](../bootstrap/src/main/java/com/github/j5ik2o/cqrs/es/java/main/RmuConfig.java)
- [GraphQL verification URLs](../tools/e2e-test/verify-group-chat.sh)

`Main` の `-w`、`-r`、`-l` はそれぞれwrite、read、local-rmuプロファイルを選びます。各プロファイルは共通設定をimportします。`APP_PERSISTENCE_*` はWriteの `RepositoryConfig`、`APP_STREAM_*` は `AppConfig.Stream` とRMU、`APP_DYNAMO_DB_*` は `AppConfig` とDynamoDBクライアント設定が参照します。`SERVER_*` と `SPRING_DATASOURCE_*` はSpring BootのサーバーとDataSource設定です。

## 既存の実行手順

- [ツール準備](TOOLS_INSTALLATION.ja.md)
- [ビルドとテスト](BUILD_AND_TEST.ja.md)
- [イメージbuild、Compose起動・停止、検証](DEBUG_ON_DOCKER_COMPOSE.ja.md)
- [ホスト上のデバッグ](DEBUG_ON_LOCAL_MACHINE.ja.md)
- [e2e実行手順](../README.ja.md#エンドツーエンドテスト)
