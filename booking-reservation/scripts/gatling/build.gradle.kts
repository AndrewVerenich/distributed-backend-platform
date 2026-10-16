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
    "-DBOOKING_URL=${System.getProperty("BOOKING_URL", "http://localhost:8230")}",
    "-DSEAT_ID=${System.getProperty("SEAT_ID", "last-1")}",
    "-DUSERS=${System.getProperty("USERS", "40")}",
    "-DHOLD_TTL_MS=${System.getProperty("HOLD_TTL_MS", "5000")}",
  )
}
