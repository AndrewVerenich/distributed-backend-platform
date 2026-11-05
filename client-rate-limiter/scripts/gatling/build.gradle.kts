plugins {
  java
  id("io.gatling.gradle") version "3.13.5"
}

repositories {
  mavenCentral()
}

configure<JavaPluginExtension> {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

tasks.withType<io.gatling.gradle.GatlingRunTask>().configureEach {
  jvmArgs = listOf(
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "-DSMART_URL=${System.getProperty("SMART_URL", "http://localhost:8221")}",
    "-DDUMB_URL=${System.getProperty("DUMB_URL", "http://localhost:8222")}",
    "-DDURATION_SECONDS=${System.getProperty("DURATION_SECONDS", "20")}",
    "-DRPS=${System.getProperty("RPS", "40")}",
    "-DBURST_USERS=${System.getProperty("BURST_USERS", "60")}",
  )
}
