# Debug on local machine.

## Run only database in docker-compose.

```shell
$ ./gradlew docker-compose-up-db
```

## Debug using IntelliJ IDEA.

The application is not running, so start it and debug it if necessary.

- bootstrap
  - src/main/java
    - com.github.j5ik2o.cqrs.es.java.main.Main

## check

```shell
$ ./gradlew verify-group-chat
```