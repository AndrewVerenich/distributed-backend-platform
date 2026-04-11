plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.springframework.boot")
  id("io.spring.dependency-management")
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  runtimeOnly("org.postgresql:r2dbc-postgresql")

  testImplementation("org.testcontainers:junit-jupiter:1.19.3")
  testImplementation("org.testcontainers:postgresql:1.19.3")
  testImplementation("org.testcontainers:r2dbc:1.19.3")
  testImplementation("io.r2dbc:r2dbc-h2")
}
