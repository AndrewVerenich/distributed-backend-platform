plugins {
  kotlin("jvm") version "1.9.20" apply false
  kotlin("plugin.spring") version "1.9.20" apply false
  id("org.springframework.boot") version "3.1.5" apply false
  id("io.spring.dependency-management") version "1.1.4" apply false
}

allprojects {
  group = "com.andver"
  version = "1.0.0"

  repositories {
    mavenCentral()
  }
}

subprojects {
  pluginManager.withPlugin("kotlin") {
    dependencies {
      "testImplementation"("org.springframework.boot:spring-boot-starter-test")
      "testImplementation"("io.projectreactor:reactor-test")
      "testImplementation"("io.mockk:mockk:1.13.8")
      "testImplementation"("com.ninja-squad:springmockk:4.0.2")
    }

    // Print individual test results to the console.
    tasks.withType<Test> {
      testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
      }
    }

    // `./gradlew test`            — unit tests only (fast, no Docker needed)
    // `./gradlew integrationTest` — integration tests that require Testcontainers/Docker
    tasks.named<Test>("test") {
      useJUnitPlatform {
        excludeTags("integration")
      }
    }

    tasks.register<Test>("integrationTest") {
      description = "Runs integration tests annotated with @Tag(\"integration\")."
      group = "verification"
      useJUnitPlatform {
        includeTags("integration")
      }
      shouldRunAfter("test")
      testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
      }
    }
  }
}
