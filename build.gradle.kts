import com.google.cloud.tools.jib.api.buildplan.ImageFormat
import io.github.kobylynskyi.graphql.codegen.gradle.GraphQLCodegenGradleTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
    id("com.diffplug.spotless") version "6.25.0"
    id("io.github.kobylynskyi.graphql.codegen") version "5.10.0"
    id("com.google.cloud.tools.jib") version "3.4.2"
}

tasks.getByName<BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = false
}

tasks.register<Exec>("docker-build") {
    commandLine("./tools/scripts/docker-build.sh")
}

tasks.register<Exec>("docker-compose-up") {
    commandLine("./tools/scripts/docker-compose-up.sh")
}

tasks.register<Exec>("docker-compose-up-db") {
    commandLine("./tools/scripts/docker-compose-up.sh", "-d")
}

tasks.register<Exec>("docker-compose-down") {
    commandLine("./tools/scripts/docker-compose-down.sh")
}

tasks.register<Exec>("verify-group-chat") {
    commandLine("./tools/e2e-test/verify-group-chat.sh")
}

allprojects {
    apply(plugin = "com.diffplug.spotless")

    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
        maven("https://repo.spring.io/snapshot")
    }

    plugins.withId("com.diffplug.spotless") {
        tasks.named("build").configure {
            dependsOn("spotlessApply")
        }
    }

    spotless {
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
        }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    group = "com.github.j5ik2o"
    version = "0.0.1-SNAPSHOT"

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependencies {
        implementation("com.google.code.findbugs:jsr305:3.0.2")
        implementation("org.projectlombok:lombok:1.18.32")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
        implementation("io.vavr:vavr:0.10.4")

        testImplementation("org.springframework.boot:spring-boot-testcontainers")
        testImplementation("org.testcontainers:junit-jupiter")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.springframework.graphql:spring-graphql-test")

        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
    }

    configurations {
        compileOnly {
            extendsFrom(annotationProcessor.get())
        }
    }

    tasks.getByName<BootJar>("bootJar") {
        enabled = false
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    spotless {
        java {
            target("src/**/*.java")
            targetExclude("**/generated/**")
            importOrder()
            removeUnusedImports()
            googleJavaFormat()
        }
    }

    tasks.test {
        enabled = false
    }
}

project(":infrastructure") {
    dependencies {
        implementation("com.github.f4b6a3:ulid-creator:5.2.3")
    }
}

project(":command:domain") {
    dependencies {
        implementation("com.github.j5ik2o:event-store-adapter-java:1.1.161")
        implementation(project(":infrastructure"))
        implementation("com.github.f4b6a3:ulid-creator:5.2.3")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    }
}

project(":command:interface-adaptor-if") {
    dependencies {
        implementation(project(":infrastructure"))
        implementation(project(":command:domain"))
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    }
}

project(":command:interface-adaptor-impl") {
    apply(plugin = "io.github.kobylynskyi.graphql.codegen")
    dependencies {
        implementation(project(":infrastructure"))
        implementation(project(":command:domain"))
        implementation(project(":command:interface-adaptor-if"))
        implementation(project(":command:processor"))
        implementation("com.github.j5ik2o:event-store-adapter-java:1.1.161")
        implementation("com.github.f4b6a3:ulid-creator:5.2.3")

        implementation("org.springframework.boot:spring-boot-starter")
        testImplementation("org.springframework.boot:spring-boot-starter-test")

        implementation("org.springframework.boot:spring-boot-starter-graphql")
        implementation("com.graphql-java:graphql-java:230521-nf-execution")

        testImplementation("org.springframework:spring-webflux")
        implementation("mysql:mysql-connector-java:8.0.33")

        testImplementation("org.testcontainers:localstack:1.19.8")
        testImplementation("org.testcontainers:mysql:1.19.8")

        implementation("software.amazon.awssdk:dynamodb:2.25.58")
        implementation("javax.validation:validation-api:2.0.1.Final")

        implementation("io.projectreactor:reactor-core:3.6.6")
    }

    tasks.named<GraphQLCodegenGradleTask>("graphqlCodegen") {
        // all config options:
        // https://github.com/kobylynskyi/graphql-java-codegen/blob/main/docs/codegen-options.md
        graphqlSchemaPaths = listOf("$projectDir/src/main/resources/graphql-write/schema.graphqls")
        outputDir = File("$projectDir/build/generated")
        packageName = "com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.graphql"
        customTypesMapping = mutableMapOf(Pair("LocalDateTime", "java.time.LocalDateTime"))
        customAnnotationsMapping =
            mutableMapOf(
                Pair(
                    "EpochMillis",
                    listOf(
                        "@com.fasterxml.jackson.databind.annotation.JsonDeserialize" +
                            "(using = com.example.json.EpochMillisScalarDeserializer.class)",
                    ),
                ),
            )
        apiReturnType = "reactor.core.publisher.Mono"
        apiReturnListType = "reactor.core.publisher.Flux"
    }

    sourceSets {
        getByName("main").java.srcDirs("$projectDir/build/generated")
    }

    tasks.named<JavaCompile>("compileJava") {
        dependsOn("graphqlCodegen")
    }
}

project(":command:processor") {
    dependencies {
        implementation(project(":infrastructure"))
        implementation(project(":command:domain"))
        implementation(project(":command:interface-adaptor-if"))
        implementation("org.springframework:spring-context")
        implementation("com.github.j5ik2o:event-store-adapter-java:1.1.161")
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
    }
}

project(":query:interface-adaptor") {
    apply(plugin = "io.github.kobylynskyi.graphql.codegen")
    dependencies {
        implementation(project(":infrastructure"))
        implementation("io.projectreactor:reactor-core:3.6.6")
        implementation("javax.validation:validation-api:2.0.1.Final")
        implementation("org.springframework:spring-context")
        implementation("org.springframework.boot:spring-boot-starter-graphql")
        implementation("com.graphql-java:graphql-java:230521-nf-execution")
        implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
        testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.3")
        testImplementation("org.flywaydb:flyway-core:10.13.0")
        testImplementation("org.flywaydb:flyway-mysql:10.13.0")
        implementation("com.tailrocks.graphql:graphql-datetime-spring-boot-starter:6.0.0")
    }

    tasks.named<GraphQLCodegenGradleTask>("graphqlCodegen") {
        // all config options:
        // https://github.com/kobylynskyi/graphql-java-codegen/blob/main/docs/codegen-options.md
        graphqlSchemaPaths = listOf("$projectDir/src/main/resources/graphql-read/schema.graphqls")
        outputDir = File("$projectDir/build/generated")
        packageName = "com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.graphql"
        customTypesMapping = mutableMapOf(Pair("LocalDateTime", "java.time.LocalDateTime"))
        customAnnotationsMapping =
            mutableMapOf(
                Pair(
                    "EpochMillis",
                    listOf(
                        "@com.fasterxml.jackson.databind.annotation.JsonDeserialize" +
                            "(using = com.example.json.EpochMillisScalarDeserializer.class)",
                    ),
                ),
            )
        apiReturnType = "reactor.core.publisher.Mono"
        apiReturnListType = "reactor.core.publisher.Flux"
    }

    sourceSets {
        getByName("main").java.srcDirs("$projectDir/build/generated")
    }

    tasks.named<JavaCompile>("compileJava") {
        dependsOn("graphqlCodegen")
    }
}

project(":rmu") {
    dependencies {
        implementation(project(":infrastructure"))
        implementation(project(":command:interface-adaptor-impl"))
        implementation(project(":command:domain"))

        implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
        testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.3")

        testImplementation("org.flywaydb:flyway-core:10.13.0")
        testImplementation("org.flywaydb:flyway-mysql:10.13.0")

        implementation("com.amazonaws:aws-lambda-java-events:3.11.5")
        implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
        implementation("com.github.j5ik2o:event-store-adapter-java:1.1.161")

        implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    }
}

project(":bootstrap") {
    apply(plugin = "com.google.cloud.tools.jib")

    tasks.getByName<BootJar>("bootJar") {
        enabled = true
        mainClass.set("com.github.j5ik2o.cqrs.es.java.main.Main")
    }

    dependencies {
        implementation("commons-cli:commons-cli:1.7.0")
        implementation("org.springframework.boot:spring-boot-starter-webflux")
        implementation(project(":command:domain"))
        implementation(project(":command:interface-adaptor-impl"))
        implementation(project(":command:processor"))
        implementation(project(":query:interface-adaptor"))
        implementation(project(":rmu"))
        implementation("com.github.j5ik2o:event-store-adapter-java:1.1.161")
        implementation("software.amazon.awssdk:dynamodb:2.25.58")
        implementation("com.amazonaws:aws-lambda-java-events:3.11.5")
        implementation("org.springframework.boot:spring-boot-configuration-processor")
        implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
    }
    jib {
        to {
            image = "cqrs-es-example-java"
        }
        from {
            image = "openjdk:17-alpine"
        }
        container {
            ports = listOf("8080")
            format = ImageFormat.OCI
        }
    }
}
