package com.andver.circuitbreaker.gatling;

final class LoadConfig {
  private LoadConfig() {}

  static String dependencyUrl() {
    return System.getProperty("DEPENDENCY_URL", "http://localhost:8100");
  }

  static String dumbUrl() {
    return System.getProperty("DUMB_URL", "http://localhost:8101");
  }

  static String guardedUrl() {
    return System.getProperty("GUARDED_URL", "http://localhost:8102");
  }

  static int durationSeconds() {
    return Integer.getInteger("DURATION_SECONDS", 45);
  }

  static double checkoutRps() {
    return Double.parseDouble(System.getProperty("CHECKOUT_RPS", "20"));
  }

  static double browseRps() {
    return Double.parseDouble(System.getProperty("BROWSE_RPS", "20"));
  }

  static long paymentDelayMs() {
    return Long.getLong("PAYMENT_DELAY_MS", 3000L);
  }
}
