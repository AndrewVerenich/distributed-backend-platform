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
  api("io.micrometer:micrometer-core")
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  enabled = false
}
tasks.getByName<Jar>("jar") {
  enabled = true
}
