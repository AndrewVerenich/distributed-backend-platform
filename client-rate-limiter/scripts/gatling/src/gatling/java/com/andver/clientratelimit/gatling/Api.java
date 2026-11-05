package com.andver.clientratelimit.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.http.HttpRequestActionBuilder;

/**
 * Hits demo client proxy endpoints. Both 200 and 429 are valid outcomes of the rate-limit demo.
 */
final class Api {
  private Api() {}

  static HttpRequestActionBuilder catalog(String name, String base) {
    return hit(name, base + "/proxy/catalog");
  }

  static HttpRequestActionBuilder checkout(String name, String base) {
    return hit(name, base + "/proxy/checkout");
  }

  private static HttpRequestActionBuilder hit(String name, String url) {
    return http(name)
      .get(url)
      .header("Accept", "application/json")
      .check(status().in(200, 429));
  }
}
