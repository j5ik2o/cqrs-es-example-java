# Docker Compose上でデバッグする

## ビルドイメージ

```shell
$ ./gradlew docker-build
```

## docker-composeの起動

```shell
$ ./gradlew docker-compose-up
```

必要なデータベースとテーブルが作成され、アプリケーションも起動します。

### docker-composeの停止

```shell
$ ./gradlew docker-compose-down
```

## 動作確認

```shell
$ ./gradlew verify-group-chat
```



