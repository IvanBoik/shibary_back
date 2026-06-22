plugins {
  kotlin("jvm") version "2.2.21"
  kotlin("plugin.spring") version "2.2.21"
  id("org.springframework.boot") version "4.0.3"
  id("io.spring.dependency-management") version "1.1.7"
}

group = "org.boiko"
version = "0.0.1-SNAPSHOT"
description = "shibary_admin (Spring Boot Admin monitoring server)"

extra["springBootAdminVersion"] = "4.0.4"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  // Standalone Spring Boot Admin monitoring server. Intentionally has NO database/business deps:
  // it must start and stay observable even when the main application or the DB is down.
  implementation("de.codecentric:spring-boot-admin-starter-server")
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-security")
  // Enables SBA's MailNotifier (registers a MailSender bean) to send email on instance status changes.
  implementation("org.springframework.boot:spring-boot-starter-mail")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("tools.jackson.module:jackson-module-kotlin")

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
  }
}
