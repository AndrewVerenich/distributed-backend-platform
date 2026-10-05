package com.andver.ratelimit.gatling;

final class LoadConfig {
  private LoadConfig() {}

  static String fixedUrl() {
    return System.getProperty("FIXED_URL", "http://localhost:8210");
  }

  static String slidingUrl() {
    return System.getProperty("SLIDING_URL", "http://localhost:8211");
  }

  static String bucketUrl() {
    return System.getProperty("BUCKET_URL", "http://localhost:8212");
  }

  static String bucketUrl2() {
    return System.getProperty("BUCKET_URL_2", "http://localhost:8213");
  }

  static int durationSeconds() {
    return Integer.getInteger("DURATION_SECONDS", 20);
  }

  static double rps() {
    return Double.parseDouble(System.getProperty("RPS", "50"));
  }

  static int catalogLimit() {
    return Integer.getInteger("CATALOG_LIMIT", 20);
  }

  static int cycles() {
    return Integer.getInteger("CYCLES", 8);
  }

  static int users() {
    return Integer.getInteger("USERS", 80);
  }
}
