package com.andver.push.gatling;

final class LoadConfig {
  private LoadConfig() {}

  static String baseUrl() {
    return System.getProperty("BASE_URL", "http://localhost:8888");
  }

  static String producerUrl() {
    return System.getProperty("PRODUCER_URL", "http://localhost:8097");
  }

  static int users() {
    return Integer.getInteger("USERS", 100);
  }

  static int durationSeconds() {
    return Integer.getInteger("DURATION_SECONDS", 30);
  }
}
