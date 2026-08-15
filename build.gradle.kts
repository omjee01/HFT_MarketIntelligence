plugins {
    java
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.hft"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

extra["grpcVersion"] = "1.65.0"
extra["protobufVersion"] = "3.25.5"
extra["resilience4jVersion"] = "2.2.0"
extra["jwtVersion"] = "4.4.0"
extra["okhttpVersion"] = "4.12.0"
extra["commonsMath3Version"] = "3.6.1"
extra["commonsLang3Version"] = "3.14.0"
extra["guavaVersion"] = "33.2.1-jre"
extra["springdocVersion"] = "2.5.0"
extra["graphqlExtendedScalarsVersion"] = "22.0"

// Force patched protobuf to satisfy CVE-2024-7254 (grpc pulls 3.25.1 transitively)
configurations.all {
    resolutionStrategy.force("com.google.protobuf:protobuf-java:${project.extra["protobufVersion"]}")
    resolutionStrategy.force("com.google.protobuf:protobuf-java-util:${project.extra["protobufVersion"]}")
}

dependencies {
    // ─── Spring Boot Starters ──────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-quartz")

    // ─── Spring Data ──────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.h2database:h2")                        // Dev in-memory DB
    runtimeOnly("org.postgresql:postgresql")                 // Production DB

    // ─── Spring Security + JWT ────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.auth0:java-jwt:${property("jwtVersion")}")

    // ─── Spring Kafka ─────────────────────────────────────────────────────────
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-streams")

    // ─── Spring Redis ─────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.apache.commons:commons-pool2")       // Redis connection pool

    // ─── Caffeine (L1 In-Process Cache) ──────────────────────────────────────
    implementation("com.github.ben-manes.caffeine:caffeine")

    // ─── Resilience4j (Circuit Breaker + Retry + Rate Limiter) ───────────────
    implementation("io.github.resilience4j:resilience4j-spring-boot3:${property("resilience4jVersion")}")

    // ─── HTTP Client ──────────────────────────────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:${property("okhttpVersion")}")
    implementation("com.squareup.okhttp3:logging-interceptor:${property("okhttpVersion")}")

    // ─── JSON ─────────────────────────────────────────────────────────────────
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names")

    // ─── Math & Utilities ─────────────────────────────────────────────────────
    implementation("org.apache.commons:commons-math3:${property("commonsMath3Version")}")
    implementation("org.apache.commons:commons-lang3:${property("commonsLang3Version")}")
    implementation("com.google.guava:guava:${property("guavaVersion")}")

    // ─── GraphQL ──────────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("com.graphql-java:graphql-java-extended-scalars:${property("graphqlExtendedScalarsVersion")}")

    // ─── gRPC ─────────────────────────────────────────────────────────────────
    implementation("io.grpc:grpc-netty-shaded:${property("grpcVersion")}")
    implementation("io.grpc:grpc-protobuf:${property("grpcVersion")}")
    implementation("io.grpc:grpc-stub:${property("grpcVersion")}")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    // ─── API Documentation ────────────────────────────────────────────────────
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")

    // ─── Lombok ───────────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ─── Testing ──────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${project.extra["protobufVersion"]}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${project.extra["grpcVersion"]}"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}
