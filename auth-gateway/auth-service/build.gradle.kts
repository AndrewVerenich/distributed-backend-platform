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
  implementation("org.springframework.security:spring-security-crypto")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("io.jsonwebtoken:jjwt-api:0.12.3")
  implementation("io.jsonwebtoken:jjwt-impl:0.12.3")
  implementation("io.jsonwebtoken:jjwt-jackson:0.12.3")
  runtimeOnly("org.postgresql:r2dbc-postgresql")

  testImplementation("org.testcontainers:junit-jupiter:1.19.3")
  testImplementation("org.testcontainers:postgresql:1.19.3")
  testImplementation("org.testcontainers:r2dbc:1.19.3")
  testImplementation("io.r2dbc:r2dbc-h2")
}
