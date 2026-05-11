plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.springframework.boot")
  id("io.spring.dependency-management")
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
  mavenCentral()
}

dependencies {
  api("org.springframework.boot:spring-boot-starter")
  api("org.springframework.boot:spring-boot-starter-actuator")
  api("org.springframework.kafka:spring-kafka")

  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("io.micrometer:micrometer-core")
  implementation("org.jetbrains.kotlin:kotlin-reflect")

  testImplementation("org.springframework.kafka:spring-kafka-test")
  testImplementation("io.micrometer:micrometer-registry-prometheus")
  testImplementation("org.awaitility:awaitility:4.2.0")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  enabled = false
}
tasks.getByName<Jar>("jar") {
  enabled = true
}

