# Local application ports and environment variables

[日本語](LOCAL_CONFIGURATION.ja.md)

This reference describes the normal local Docker Compose stack. Values were checked against main at `30f1cbc464734f4abf23b0f80e5c92757ae48ff9`.

## Applications and ports

| Application and argument | Role | Host port | Container port | GraphQL URL from the host |
| --- | --- | --- | --- | --- |
| Write `-w` | Writes events through mutations | 58080 | 8080 | `http://localhost:58080/graphql` |
| RMU `-l` | Updates MySQL read models from head Streams and the journal | 58081 | 8080 | No user GraphQL API |
| Read `-r` | Queries read models in MySQL | 58082 | 8080 | `http://localhost:58082/graphql` |

Compose declares the RMU port mapping. The RMU runs in the background; users call Write and Read. Read model updates are asynchronous. Container-to-container API URLs are `http://write-api-server-1:8080/graphql` and `http://read-api-server-1:8080/graphql`.

The e2e Compose override clears service `ports`, so it publishes none of the host ports above. Its test container uses the internal URLs.

## Environment variables by application

Compose supplies the values below to containers; no matching exports in the host shell are required. Defaults are from `application-common.yml`. Environment overrides are optional, but a manually started process needs the port, LocalStack endpoint, and database settings changed to match this stack.

| Environment variable | Application | Compose value | File default | Meaning |
| --- | --- | --- | --- | --- |
| `SERVER_ADDRESS` | Write, RMU, Read | `0.0.0.0` | `未指定 / unset` | Bind address. Compose binds all interfaces. |
| `SERVER_PORT` | Write, RMU, Read | `8080` | `48080` | Application listener port, distinct from the published host port. |
| `APP_PERSISTENCE_JOURNAL_TABLE_NAME` | Write | `journal` | `journal` | Event journal table. |
| `APP_PERSISTENCE_SNAPSHOT_TABLE_NAME` | Write | `snapshot` | `snapshot` | Snapshot table. |
| `APP_PERSISTENCE_HEAD_TABLE_NAME` | Write | `head` | `head` | Table of aggregate head positions. |
| `APP_PERSISTENCE_SNAPSHOT_AID_INDEX_NAME` | Write | `snapshot-aid-index` | `snapshot-aid-index` | Snapshot GSI name. Must match table setup. |
| `APP_PERSISTENCE_SNAPSHOT_INTERVAL` | Write | `10` | `10` | Event interval for snapshots. |
| `APP_DYNAMO_DB_REGION` | Write, RMU | `ap-northeast-1` | `ap-northeast-1` | Region for DynamoDB and Streams. |
| `APP_DYNAMO_DB_ENDPOINT_URL` | Write, RMU | `http://localstack:4566` | `http://localhost:8000` | AWS endpoint. Compose uses LocalStack. |
| `APP_DYNAMO_DB_ACCESS_KEY_ID` | Write, RMU | `x` | `設定なし / none` | Compose variable. See the access-key naming difference below. |
| `APP_DYNAMO_DB_SECRET_ACCESS_KEY` | Write, RMU | `x` | `x` | Dummy local secret key. |
| `APP_STREAM_HEAD_TABLE_NAME` | RMU | `head` | `head` | Head table for Streams and startup reconciliation. |
| `APP_STREAM_JOURNAL_TABLE_NAME` | RMU | `journal` | `journal` | Journal used to fill missing events. |
| `APP_STREAM_MAX_ITEM_COUNT` | RMU | `32` | `32` | Maximum records per Streams fetch. |
| `SPRING_DATASOURCE_URL` | RMU, Read | `jdbc:mysql://mysql-local:3306/ceer` | `jdbc:mysql://localhost:3306/test` | JDBC URL of the Read Model DB. |
| `SPRING_DATASOURCE_USERNAME` | RMU, Read | `ceer` | `sa` | Read Model DB username. |
| `SPRING_DATASOURCE_PASSWORD` | RMU, Read | `ceer` | `空 / empty` | Read Model DB password. |

Write requires persistence table names and the snapshot GSI to match database setup. RMU head and journal names must refer to the Write tables. RMU and Read require the same MySQL database and migrated schema. Write stores events in DynamoDB.

`SERVER_ADDRESS` has no file default; when omitted, server binding follows the server default. Three processes run directly on the host need distinct `SERVER_PORT` values to avoid the shared default `48080`. Host processes reach LocalStack at `http://localhost:4566` and MySQL at `jdbc:mysql://localhost:3306/ceer`. Normal Compose also publishes database ports `4566` and `3306`.

### AWS access key naming difference

Compose sets `APP_DYNAMO_DB_ACCESS_KEY_ID=x`, but the implementation reads `app.dynamo-db.access-key`. There is no explicit mapping from `access-key-id`. Local execution has `access-key: x` in the common configuration. A different credential requires overriding `access-key` itself; changing only `ACCESS_KEY_ID` is insufficient.

Compose also sets `AWS_REGION=ap-northeast-1` for all three applications. Explicit DynamoDB client configuration uses `APP_DYNAMO_DB_REGION`. The common file supplies the region, endpoint, and keys, so the local configuration uses an explicit endpoint and static credentials.

### Optional RMU settings

Compose leaves these optional overrides unset. The common file and `AppConfig.Stream` supply the same defaults. See [RMU recovery](RMU_RECOVERY.md).

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `APP_STREAM_MAX_EVENTS_PER_TRANSACTION` | `1` | Maximum events per transaction |
| `APP_STREAM_RETRY_MAX_ATTEMPTS` | `5` | Maximum retry attempts |
| `APP_STREAM_RETRY_MAX_ELAPSED_MILLIS` | `30000` | Retry time budget in milliseconds |
| `APP_STREAM_RETRY_INITIAL_DELAY_MILLIS` | `200` | Initial retry delay in milliseconds |
| `APP_STREAM_RETRY_MAX_DELAY_MILLIS` | `5000` | Maximum retry delay in milliseconds |
| `APP_STREAM_POLL_BACKOFF_INITIAL_MILLIS` | `500` | Initial polling backoff in milliseconds |
| `APP_STREAM_POLL_BACKOFF_MAX_MILLIS` | `30000` | Maximum polling backoff in milliseconds |

## Configuration sources

- [Application Compose](../tools/docker-compose/docker-compose-applications.yml)
- [Database Compose](../tools/docker-compose/docker-compose-databases.yml)
- [e2e Compose](../tools/docker-compose/docker-compose-e2e-test.yml)
- [Common defaults](../bootstrap/src/main/resources/application-common.yml)
- [AppConfig](../bootstrap/src/main/java/com/github/j5ik2o/cqrs/es/java/main/AppConfig.java)
- [Write repository configuration](../command/interface-adaptor-impl/src/main/java/com/github/j5ik2o/cqrs/es/java/command/interface_adaptor/repository/RepositoryConfig.java)
- [DynamoDB clients](../bootstrap/src/main/java/com/github/j5ik2o/cqrs/es/java/main/DynamoDbConfig.java)
- [RMU configuration](../bootstrap/src/main/java/com/github/j5ik2o/cqrs/es/java/main/RmuConfig.java)
- [GraphQL verification URLs](../tools/e2e-test/verify-group-chat.sh)

`Main` selects write, read, or local-rmu with `-w`, `-r`, or `-l`. Each profile imports the common file. Write `RepositoryConfig` consumes `APP_PERSISTENCE_*`; `AppConfig.Stream` and RMU consume `APP_STREAM_*`. `AppConfig` and DynamoDB client configuration consume `APP_DYNAMO_DB_*`. Spring Boot server and DataSource configuration consume `SERVER_*` and `SPRING_DATASOURCE_*`.

## Existing run instructions

- [Tool setup](TOOLS_INSTALLATION.md)
- [Build and test](BUILD_AND_TEST.md)
- [Image build, Compose start, stop, and verification](DEBUG_ON_DOCKER_COMPOSE.md)
- [Host debugging](DEBUG_ON_LOCAL_MACHINE.md)
- [End-to-end test instructions](../README.md#end-to-end-test)
