package com.andver.idempotency.gatling;

final class LoadConfig {
  private LoadConfig() {}

  static String chargeUrl() {
    return System.getProperty("CHARGE_URL", "http://localhost:8220");
  }

  static int users() {
    return Integer.parseInt(System.getProperty("USERS", "40"));
  }

  static long amount() {
    return Long.parseLong(System.getProperty("AMOUNT", "1"));
  }

  static String owner() {
    return System.getProperty("OWNER", "alice");
  }
}
