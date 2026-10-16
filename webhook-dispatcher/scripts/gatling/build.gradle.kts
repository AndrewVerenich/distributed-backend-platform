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
    "-DWEBHOOK_API_URL=${System.getProperty("WEBHOOK_API_URL", "http://localhost:8240")}",
    "-DUSERS=${System.getProperty("USERS", "20")}",
  )
}
