package com.andver.ratelimit.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.http.HttpRequestActionBuilder;

/** Calls that must come back as 200 or 429 and always carry the quota headers. */
final class Api {
  private Api() {}

  static HttpRequestActionBuilder catalog(String name, String base, String userId) {
    return hit(name, "GET", base + "/api/catalog/sku-1", userId);
  }

  static HttpRequestActionBuilder checkout(String name, String base, String userId) {
    return hit(name, "POST", base + "/api/checkout", userId);
  }

  private static HttpRequestActionBuilder hit(String name, String method, String url, String userId) {
    HttpRequestActionBuilder request = "POST".equals(method)
      ? http(name).post(url).body(StringBody("{}")).header("Content-Type", "application/json")
      : http(name).get(url);
    return request
      .header("X-User-Id", userId)
      .header("Accept", "application/json")
      .check(status().in(200, 429))
      .check(header("X-RateLimit-Limit").exists())
      .check(header("X-RateLimit-Remaining").exists())
      .check(header("X-RateLimit-Reset").exists())
      .check(header("X-RateLimit-Shard").exists());
  }
}
