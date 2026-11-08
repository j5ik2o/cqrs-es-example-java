# Lambda package size

The RMU Lambda package (`:rmu:lambdaZip`) is built at
`rmu/build/distributions/rmu-lambda.zip`. It contains the handler classes at the
ZIP root and every runtime dependency as a JAR under `lib/`. This page records
how to measure it and the current breakdown. It is measurement only: no layer
split and no dependency exclusion is applied yet.

## Measuring

```sh
./gradlew :rmu:lambdaZip
./deploy/report-lambda-package-size.sh
```

The script prints the compressed ZIP size, the uncompressed size, the number of
files, the size of `lib/`, the 20 largest JARs, and the JARs that come from the
Spanner, Bigtable, and SQLite backends. `TOP` changes how many JARs are listed.
The script has no pass/fail threshold. CI runs it in the `deploy-assets` job on
every pull request and logs the same output.

To see where a dependency comes from:

```sh
./gradlew :rmu:dependencyInsight --configuration runtimeClasspath --dependency google-cloud-spanner
./gradlew :rmu:dependencies --configuration runtimeClasspath
```

## Measured on 2026-11-08

Built with JDK 17 (Temurin 17.0.21) and Gradle 8.14.6 on macOS aarch64.

| Item | Bytes | MiB |
|:--|--:|--:|
| ZIP (compressed) | 124,947,901 | 119.16 |
| Uncompressed | 135,222,173 | 128.96 |
| `lib/` (190 JARs) | 135,029,726 | 128.77 |
| Files in the ZIP | 250 | |

The ZIP barely compresses because its payload is already-compressed JARs.

### Largest JARs in `lib/`

| # | Bytes | MiB | JAR |
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

## Event store adapter backends

`io.github.j5ik2o:event-store-adapter-java:5.1.0` puts all three of its storage
backends on the runtime classpath. The RMU only uses DynamoDB, but the package
carries them all.

| Backend | JARs | Bytes | MiB | Pulled in by |
|:--|--:|--:|--:|:--|
| SQLite | 1 | 14,317,659 | 13.65 | `org.xerial:sqlite-jdbc:3.53.4.0` → 3.49.1.0 |
| Spanner | 7 | 4,622,693 | 4.41 | `com.google.cloud:google-cloud-spanner:6.124.0` |
| Bigtable | 4 | 5,532,064 | 5.28 | `com.google.cloud:google-cloud-bigtable:2.85.0` |

`dependencyInsight` confirms all three come from
`io.github.j5ik2o:event-store-adapter-java:5.1.0`. Spanner and Bigtable also
pull the gRPC stack (25 JARs, 25.32 MiB), `protobuf-java`, `google-cloud-core`,
and the Google auth/HTTP clients, so their real cost is larger than the table
shows.

## Out of scope

Layer splitting and dependency exclusion are deliberately not done here. If the
package needs to shrink, the next steps would be splitting the Google Cloud and
SQLite backends out of the Lambda classpath, or shipping the RMU as a container
image. Both need a separate decision.
