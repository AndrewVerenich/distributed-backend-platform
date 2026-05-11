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
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  implementation(project(":distributed-hash-map:map-starter"))
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  enabled = true
}
