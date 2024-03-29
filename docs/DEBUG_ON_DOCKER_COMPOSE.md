# Debugging on Docker Compose

## Build the image

```shell
$ ./gradlew docker-build
```

## Start docker-compose

```shell
$ ./gradlew docker-compose-up
```

The required database and tables will be created and the application will be started.

### Stop docker-compose

```shell
$ ./gradlew docker-compose-down
```

## Verification

```shell
$ ./gradlew verify-group-chat
```



