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
    "-DOFF_URL=${System.getProperty("OFF_URL", "http://localhost:8221")}",
    "-DLOCAL_URL=${System.getProperty("LOCAL_URL", "http://localhost:8222")}",
    "-DDISTRIBUTED_URL=${System.getProperty("DISTRIBUTED_URL", "http://localhost:8223")}",
    "-DUPSTREAM_URL=${System.getProperty("UPSTREAM_URL", "http://localhost:8220")}",
    "-DUSERS=${System.getProperty("USERS", "80")}",
    "-DDURATION_SECONDS=${System.getProperty("DURATION_SECONDS", "10")}",
    "-DPRODUCT_ID=${System.getProperty("PRODUCT_ID", "hot-42")}",
  )
}
