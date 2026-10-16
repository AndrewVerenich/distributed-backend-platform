plugins {
  kotlin("jvm")
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
  mavenCentral()
}

tasks.getByName<Jar>("jar") {
  enabled = true
}
