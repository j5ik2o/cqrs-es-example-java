import io.github.kobylynskyi.graphql.codegen.gradle.GraphQLCodegenGradleTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
    id("com.diffplug.spotless") version "6.25.0"
    id("io.github.kobylynskyi.graphql.codegen") version "5.10.0"
}

tasks.getByName<BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = false
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
        implementation("org.projectlombok:lombok:1.18.30")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
        implementation("io.vavr:vavr:0.10.4")

        testImplementation("org.springframework.boot:spring-boot-testcontainers")
        testImplementation("org.testcontainers:junit-jupiter")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.springframework.graphql:spring-graphql-test")
    }

    tasks.getByName<BootJar>("bootJar") {
        enabled = false
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    spotless {
        java {
            importOrder()
            removeUnusedImports()
            googleJavaFormat()
        }
    }
}

project(":infrastructure") {
    dependencies {
        implementation("com.github.f4b6a3:ulid-creator:5.2.3")
    }
}

project(":command:domain") {
    dependencies {
        implementation("com.github.j5ik2o:event-store-adapter-java:1.1.106")
        implementation(project(":infrastructure"))
        implementation("com.github.f4b6a3:ulid-creator:5.2.3")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    }
}

project(":command:interface-adaptor-if") {
    dependencies {
        implementation(project(":infrastructure"))
        implementation(project(":command:domain"))
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    }
}

project(":command:interface-adaptor-impl") {
    apply(plugin = "io.github.kobylynskyi.graphql.codegen")
    dependencies {
        implementation(project(":infrastructure"))
        implementation(project(":command:domain"))
        implementation(project(":command:interface-adaptor-if"))
        implementation("com.github.j5ik2o:event-store-adapter-java:1.1.106")
        implementation("com.github.f4b6a3:ulid-creator:5.2.3")

        implementation("org.springframework.boot:spring-boot-starter")
        testImplementation("org.springframework.boot:spring-boot-starter-test")

        implementation("com.graphql-java:graphql-java:21.3")
        implementation("com.graphql-java-kickstart:graphql-java-tools:13.0.3")
        implementation("com.graphql-java-kickstart:graphql-spring-boot-starter:15.1.0")
        testImplementation("com.graphql-java-kickstart:graphql-spring-boot-starter-test:15.1.0")

        testImplementation("org.springframework:spring-webflux")
        implementation("mysql:mysql-connector-java:8.0.33")

        implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:2.2.2")
        testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.2.2")

        testImplementation("org.flywaydb:flyway-core:10.8.1")
        testImplementation("org.flywaydb:flyway-mysql:10.8.1")

        testImplementation("org.testcontainers:localstack:1.19.6")
        testImplementation("org.testcontainers:mysql:1.19.6")

        testImplementation("software.amazon.awssdk:dynamodb:2.25.1")
        implementation("javax.validation:validation-api:2.0.1.Final")
    }

    tasks.named<GraphQLCodegenGradleTask>("graphqlCodegen") {
        // all config options:
        // https://github.com/kobylynskyi/graphql-java-codegen/blob/main/docs/codegen-options.md
        graphqlSchemaPaths = listOf("$projectDir/src/main/resources/graphql/schema.graphqls")
        outputDir = File("$projectDir/build/generated")
        packageName = "com.github.j5ik2o.cqrs.es.java.interface_adaptor.graphql"
        customTypesMapping = mutableMapOf(Pair("EpochMillis", "java.time.LocalDateTime"))
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
        parentInterfaces {
            queryResolver = "graphql.kickstart.tools.GraphQLQueryResolver"
            mutationResolver = "graphql.kickstart.tools.GraphQLMutationResolver"
        }
    }

    // Automatically generate GraphQL code on project build:
    sourceSets {
        getByName("main").java.srcDirs("$projectDir/build/generated")
    }

// Add generated sources to your project source sets:
    tasks.named<JavaCompile>("compileJava") {
        dependsOn("graphqlCodegen")
    }
}

project(":command:processor") {
    dependencies {
        implementation(project(":infrastructure"))
        implementation(project(":command:domain"))
        implementation(project(":command:interface-adaptor-if"))
        implementation("com.github.j5ik2o:event-store-adapter-java:1.1.106")
    }
}

project(":bootstrap") {

    tasks.getByName<BootJar>("bootJar") {
        enabled = true
        mainClass.set("com.github.j5ik2o.cqrs.es.java.main.Main")
    }

    dependencies {
        implementation("org.springframework.boot:spring-boot-autoconfigure")
        implementation("org.springframework.boot:spring-boot-starter-webflux")
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("com.graphql-java-kickstart:graphql-spring-boot-starter:15.1.0")
        implementation(project(":command:domain"))
        implementation(project(":command:interface-adaptor-impl"))
        implementation(project(":command:processor"))
        implementation(project(":query:interface-adaptor"))
        implementation(project(":rmu"))
    }
}
