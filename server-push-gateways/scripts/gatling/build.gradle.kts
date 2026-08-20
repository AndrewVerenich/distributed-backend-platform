plugins {
  java
  id("io.gatling.gradle") version "3.13.5"
}

repositories {
  mavenCentral()
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

tasks.withType<io.gatling.gradle.GatlingRunTask>().configureEach {
  jvmArgs = listOf(
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "-DBASE_URL=${System.getProperty("BASE_URL", "http://localhost:8888")}",
    "-DPRODUCER_URL=${System.getProperty("PRODUCER_URL", "http://localhost:8097")}",
    "-DUSERS=${System.getProperty("USERS", "100")}",
    "-DDURATION_SECONDS=${System.getProperty("DURATION_SECONDS", "30")}",
  )
}
