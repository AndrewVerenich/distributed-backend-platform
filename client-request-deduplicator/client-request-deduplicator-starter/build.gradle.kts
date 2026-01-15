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
  api("org.springframework.boot:spring-boot-starter-webflux")
  api("org.springframework.boot:spring-boot-starter-data-redis")
  api("com.fasterxml.jackson.module:jackson-module-kotlin")
  api("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  enabled = false
}
tasks.getByName<Jar>("jar") {
  enabled = true
}
