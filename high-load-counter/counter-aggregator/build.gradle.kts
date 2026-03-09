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
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("org.apache.kafka:kafka-streams")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
