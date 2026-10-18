package com.andver.singleflight.gatling;

final class LoadConfig {
  private LoadConfig() {}

  static String offUrl() {
    return System.getProperty("OFF_URL", "http://localhost:8221");
  }

  static String localUrl() {
    return System.getProperty("LOCAL_URL", "http://localhost:8222");
  }

  static String distributedUrl() {
    return System.getProperty("DISTRIBUTED_URL", "http://localhost:8223");
  }

  static String upstreamUrl() {
    return System.getProperty("UPSTREAM_URL", "http://localhost:8220");
  }

  static int users() {
    return Integer.parseInt(System.getProperty("USERS", "80"));
  }

  static int durationSeconds() {
    return Integer.parseInt(System.getProperty("DURATION_SECONDS", "10"));
  }

  static String productId() {
    return System.getProperty("PRODUCT_ID", "hot-42");
  }
}
