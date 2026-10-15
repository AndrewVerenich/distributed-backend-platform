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
    "-DDEPENDENCY_URL=${System.getProperty("DEPENDENCY_URL", "http://localhost:8100")}",
    "-DDUMB_URL=${System.getProperty("DUMB_URL", "http://localhost:8101")}",
    "-DGUARDED_URL=${System.getProperty("GUARDED_URL", "http://localhost:8102")}",
    "-DDURATION_SECONDS=${System.getProperty("DURATION_SECONDS", "45")}",
    "-DCHECKOUT_RPS=${System.getProperty("CHECKOUT_RPS", "20")}",
    "-DBROWSE_RPS=${System.getProperty("BROWSE_RPS", "20")}",
    "-DPAYMENT_DELAY_MS=${System.getProperty("PAYMENT_DELAY_MS", "3000")}",
  )
}
