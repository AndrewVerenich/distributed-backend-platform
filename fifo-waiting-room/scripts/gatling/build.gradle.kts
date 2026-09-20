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
    "-DWAITING_ROOM_URL=${System.getProperty("WAITING_ROOM_URL", "http://localhost:8100")}",
    "-DCHECKOUT_URL=${System.getProperty("CHECKOUT_URL", "http://localhost:8101")}",
    "-DEVENT_ID=${System.getProperty("EVENT_ID", "flash-sale-1")}",
    "-DUSERS=${System.getProperty("USERS", "100")}",
    "-DDURATION_SECONDS=${System.getProperty("DURATION_SECONDS", "60")}",
  )
}
