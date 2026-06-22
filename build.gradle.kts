plugins {
  kotlin("jvm") version "2.2.21"
  kotlin("plugin.spring") version "2.2.21"
  id("org.springframework.boot") version "4.0.3"
  id("io.spring.dependency-management") version "1.1.7"
}

group = "org.boiko"
version = "0.0.1-SNAPSHOT"
description = "shibary_back"

extra["springBootAdminVersion"] = "4.0.4"
extra["awsSdkVersion"] = "2.31.6"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  implementation("org.liquibase:liquibase-core")
  implementation("org.postgresql:postgresql")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("tools.jackson.module:jackson-module-kotlin")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  // This artifact is an SBA *client* only; the monitoring server lives in the :sba-server module.
  implementation("de.codecentric:spring-boot-admin-starter-client")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-mail")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

  // S3-compatible object storage (AWS/MinIO/Yandex). Files are uploaded directly by the client via
  // presigned URLs; the backend only mints those URLs and performs HEAD/DELETE maintenance.
  implementation("software.amazon.awssdk:s3")
  implementation("software.amazon.awssdk:url-connection-client")

  testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

dependencyManagement {
  imports {
    mavenBom("de.codecentric:spring-boot-admin-dependencies:${property("springBootAdminVersion")}")
    mavenBom("software.amazon.awssdk:bom:${property("awsSdkVersion")}")
  }
}
