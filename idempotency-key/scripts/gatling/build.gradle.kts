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
    "-DCHARGE_URL=${System.getProperty("CHARGE_URL", "http://localhost:8220")}",
    "-DUSERS=${System.getProperty("USERS", "40")}",
    "-DAMOUNT=${System.getProperty("AMOUNT", "1")}",
    "-DOWNER=${System.getProperty("OWNER", "alice")}",
  )
}
