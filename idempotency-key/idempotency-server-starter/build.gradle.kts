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
  api(project(":idempotency-key:idempotency-api"))
  api("org.springframework.boot:spring-boot-starter-webflux")
  api("org.springframework.boot:spring-boot-starter-data-r2dbc")
  api("io.micrometer:micrometer-core")
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.testcontainers:junit-jupiter:1.19.3")
  testImplementation("org.testcontainers:postgresql:1.19.3")
  testImplementation("org.testcontainers:r2dbc:1.19.3")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  enabled = false
}
tasks.getByName<Jar>("jar") {
  enabled = true
}
