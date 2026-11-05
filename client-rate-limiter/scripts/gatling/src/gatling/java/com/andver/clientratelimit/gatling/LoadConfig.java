package com.andver.clientratelimit.gatling;

final class LoadConfig {
  private LoadConfig() {}

  static String smartUrl() {
    return System.getProperty("SMART_URL", "http://localhost:8221");
  }

  static String dumbUrl() {
    return System.getProperty("DUMB_URL", "http://localhost:8222");
  }

  static int durationSeconds() {
    return Integer.getInteger("DURATION_SECONDS", 20);
  }

  static double rps() {
    return Double.parseDouble(System.getProperty("RPS", "40"));
  }

  static int burstUsers() {
    return Integer.getInteger("BURST_USERS", 60);
  }
}
