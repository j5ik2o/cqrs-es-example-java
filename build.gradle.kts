import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
    id("com.diffplug.spotless") version "6.25.0"
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
        implementation("com.github.j5ik2o:event-store-adapter-java:1.1.105")
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
    dependencies {
        implementation(project(":infrastructure"))
        implementation(project(":command:domain"))
        implementation(project(":command:interface-adaptor-if"))
        implementation("com.github.j5ik2o:event-store-adapter-java:1.1.105")
        implementation("com.github.f4b6a3:ulid-creator:5.2.3")

        implementation("org.springframework.boot:spring-boot-starter-graphql")
        testImplementation("org.springframework:spring-webflux")
        implementation("mysql:mysql-connector-java:8.0.33")

        implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:2.2.2")
        testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:2.2.2")

        testImplementation("org.flywaydb:flyway-core:8.5.13")
        testImplementation("org.flywaydb:flyway-mysql:8.5.13")

        testImplementation("org.testcontainers:localstack:1.19.2")
        testImplementation("org.testcontainers:mysql:1.17.3")

        testImplementation("software.amazon.awssdk:dynamodb:2.21.26")
    }
}

project(":command:use-case") {
    dependencies {
        implementation(project(":infrastructure"))
        implementation(project(":command:domain"))
        implementation(project(":command:interface-adaptor-if"))
    }
}

project(":bootstrap") {

    tasks.getByName<BootJar>("bootJar") {
        enabled = true
        mainClass.set("com.github.j5ik2o.cqrs.es.java.main.Main")
    }

    dependencies {
        implementation("org.springframework.boot:spring-boot-autoconfigure")
        implementation(project(":command:domain"))
        implementation(project(":command:interface-adaptor-impl"))
        implementation(project(":command:use-case"))
        implementation(project(":query:interface-adaptor"))
        implementation(project(":rmu"))
    }
}
