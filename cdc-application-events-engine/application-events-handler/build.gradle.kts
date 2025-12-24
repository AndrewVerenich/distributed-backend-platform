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
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("org.apache.camel.springboot:camel-spring-boot-starter:4.4.0")
  implementation("org.apache.camel.springboot:camel-kafka-starter:4.4.0")
  implementation("org.apache.camel.springboot:camel-yaml-dsl-starter:4.4.0")
}
