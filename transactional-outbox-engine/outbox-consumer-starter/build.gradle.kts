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
  api("org.springframework.kafka:spring-kafka")
  api("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  api(project(":transactional-outbox-engine:outbox-model"))
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  enabled = false
}
tasks.getByName<Jar>("jar") {
  enabled = true
}
