# Lambda 配布物サイズ

RMU の Lambda 配布物（`:rmu:lambdaZip`）は
`rmu/build/distributions/rmu-lambda.zip` に出力される。ハンドラクラスが ZIP
直下に入り、実行時依存の JAR がすべて `lib/` 配下に入る。このページは計測方法と
現状の内訳を記録する。計測のみを目的としており、レイヤー分割や依存の除外はまだ
行っていない。

## 計測方法

```sh
./gradlew :rmu:lambdaZip
./deploy/report-lambda-package-size.sh
```

このスクリプトは、ZIP の圧縮サイズ、展開後のサイズ、ファイル数、`lib/` の
サイズ、大きい順の JAR 20 件、およびファイル名が Spanner / Bigtable / SQLite
バックエンドに一致する JAR を出力する。バックエンドの由来は次の節の
`dependencyInsight` で確認している。`TOP` で一覧の件数を変えられる。合否の
しきい値は持たない。CI では `deploy-assets` ジョブが pull request ごとに実行し、
同じ内容をログに出す。

依存の由来を確認するには次のコマンドを使う。

```sh
./gradlew :rmu:dependencyInsight --configuration runtimeClasspath --dependency google-cloud-spanner
./gradlew :rmu:dependencies --configuration runtimeClasspath
```

## 2026-11-08 の計測値

JDK 17（Temurin 17.0.21）、Gradle 8.14.6、macOS aarch64 で計測した。

| 項目 | バイト | MiB |
|:--|--:|--:|
| ZIP（圧縮後） | 124,947,901 | 119.16 |
| 展開後 | 135,222,173 | 128.96 |
| `lib/`（JAR 190 件） | 135,029,726 | 128.77 |
| ZIP 内のファイル数 | 250 | |

ZIP の中身が圧縮済みの JAR なので、圧縮してもサイズはほとんど減らない。ZIP の
エントリはタイムスタンプを持つため、圧縮後サイズはビルドごとに数バイト変わる。
展開後のサイズは変わらない。

### `lib/` 内の大きい JAR

| # | バイト | MiB | JAR |
|--:|--:|--:|:--|
| 1 | 14,317,659 | 13.65 | sqlite-jdbc-3.49.1.0.jar |
| 2 | 12,310,552 | 11.74 | grpc-xds-1.83.0.jar |
| 3 | 10,837,690 | 10.34 | grpc-netty-shaded-1.83.0.jar |
| 4 | 8,682,747 | 8.28 | bcprov-jdk18on-1.80.jar |
| 5 | 7,548,583 | 7.20 | conscrypt-openjdk-uber-2.6.2.jar |
| 6 | 3,756,768 | 3.58 | graphql-java-24.3.jar |
| 7 | 3,139,372 | 2.99 | dynamodb-2.57.11.jar |
| 8 | 3,064,793 | 2.92 | guava-33.6.0-jre.jar |
| 9 | 2,830,165 | 2.70 | proto-google-common-protos-2.78.0.jar |
| 10 | 2,607,237 | 2.49 | mysql-connector-j-9.7.0.jar |
| 11 | 2,469,561 | 2.36 | tink-1.16.0.jar |
| 12 | 2,113,305 | 2.02 | proto-google-cloud-bigtable-admin-v2-2.85.0.jar |
| 13 | 2,096,068 | 2.00 | spring-web-6.2.19.jar |
| 14 | 2,088,460 | 1.99 | spring-boot-autoconfigure-3.5.16.jar |
| 15 | 2,055,065 | 1.96 | lombok-1.18.48.jar |
| 16 | 2,036,851 | 1.94 | proto-google-cloud-monitoring-v3-3.100.0.jar |
| 17 | 1,983,775 | 1.89 | spring-core-6.2.19.jar |
| 18 | 1,955,738 | 1.87 | google-cloud-spanner-6.124.0.jar |
| 19 | 1,904,513 | 1.82 | spring-boot-3.5.16.jar |
| 20 | 1,899,418 | 1.81 | reactor-core-3.7.19.jar |

## イベントストアアダプタのバックエンド

`io.github.j5ik2o:event-store-adapter-java:5.1.0` は 3 つのストレージ
バックエンドをすべて実行時クラスパスに載せる。RMU が使うのは DynamoDB だけだが、
配布物には 3 つとも含まれる。

| バックエンド | JAR 数 | バイト | MiB | 取り込み元 |
|:--|--:|--:|--:|:--|
| SQLite | 1 | 14,317,659 | 13.65 | `org.xerial:sqlite-jdbc:3.53.4.0` → 3.49.1.0 |
| Spanner | 7 | 4,622,693 | 4.41 | `com.google.cloud:google-cloud-spanner:6.124.0` |
| Bigtable | 4 | 5,532,064 | 5.28 | `com.google.cloud:google-cloud-bigtable:2.85.0` |

`dependencyInsight` で確認すると、3 つとも
`io.github.j5ik2o:event-store-adapter-java:5.1.0` が由来である。Spanner と
Bigtable は gRPC 一式（JAR 25 件、25.32 MiB）、`protobuf-java`、
`google-cloud-core`、Google の認証・HTTP クライアントも連れてくるため、実際の
コストは表の数字より大きい。

## 対象外

レイヤー分割と依存の除外はここでは意図的に行っていない。サイズを減らす必要が
出た場合は、Google Cloud と SQLite のバックエンドを Lambda のクラスパスから
外すか、RMU をコンテナイメージで配布するかになる。どちらも別途判断が必要になる。
