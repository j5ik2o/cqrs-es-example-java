# ローカルマシンでデバッグ

## docker-composeにてデータベースだけを実行する。

```shell
$ ./gradlew docker-compose-up-db
```

## IntelliJ IDEAを使ってデバッグする。

アプリケーションは動作していないので必要に応じて起動してデバッグしてください。

- bootstrap
  - src/main/java
    - com.github.j5ik2o.cqrs.es.java.main.Main

## 動作確認

```shell
$ ./gradlew verify-group-chat
```
