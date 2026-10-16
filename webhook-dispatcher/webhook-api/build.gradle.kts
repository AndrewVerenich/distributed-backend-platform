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
  implementation(project(":webhook-dispatcher:webhook-core"))
  implementation(project(":webhook-dispatcher:webhook-store"))
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}

tasks.getByName<Jar>("jar") {
  enabled = false
}
