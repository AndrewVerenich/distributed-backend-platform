plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.springframework.boot")
  id("io.spring.dependency-management")
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.cloud:spring-cloud-dependencies:2022.0.4")
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
