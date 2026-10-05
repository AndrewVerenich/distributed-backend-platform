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
    "-DFIXED_URL=${System.getProperty("FIXED_URL", "http://localhost:8210")}",
    "-DSLIDING_URL=${System.getProperty("SLIDING_URL", "http://localhost:8211")}",
    "-DBUCKET_URL=${System.getProperty("BUCKET_URL", "http://localhost:8212")}",
    "-DBUCKET_URL_2=${System.getProperty("BUCKET_URL_2", "http://localhost:8213")}",
    "-DDURATION_SECONDS=${System.getProperty("DURATION_SECONDS", "20")}",
    "-DRPS=${System.getProperty("RPS", "50")}",
    "-DCATALOG_LIMIT=${System.getProperty("CATALOG_LIMIT", "20")}",
    "-DCYCLES=${System.getProperty("CYCLES", "8")}",
    "-DUSERS=${System.getProperty("USERS", "80")}",
  )
}
