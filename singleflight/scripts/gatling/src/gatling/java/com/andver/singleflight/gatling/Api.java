package com.andver.singleflight.gatling;

import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.http.HttpRequestActionBuilder;

final class Api {
  private Api() {}

  static HttpRequestActionBuilder product(String name, String base, String productId) {
    return http(name)
      .get(base + "/products/" + productId)
      .check(status().is(200));
  }
}
