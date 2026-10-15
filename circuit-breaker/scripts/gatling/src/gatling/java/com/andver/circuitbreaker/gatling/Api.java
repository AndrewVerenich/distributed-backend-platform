package com.andver.circuitbreaker.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.http.HttpRequestActionBuilder;

final class Api {
  private Api() {}

  static HttpRequestActionBuilder setPaymentSlow(String dependencyBase, long delayMs) {
    String body =
      "{\"paymentMode\":\"SLOW\",\"errorRate\":1.0,\"delayMs\":" + delayMs + "}";
    return http("control-payment-slow")
      .post(dependencyBase + "/control")
      .header("Content-Type", "application/json")
      .body(StringBody(body))
      .check(status().is(200));
  }

  static HttpRequestActionBuilder setPaymentOk(String dependencyBase) {
    return http("control-payment-ok")
      .post(dependencyBase + "/control")
      .header("Content-Type", "application/json")
      .body(StringBody("{\"paymentMode\":\"OK\",\"errorRate\":0.0,\"delayMs\":0}"))
      .check(status().is(200));
  }

  static HttpRequestActionBuilder setPaymentError(String dependencyBase) {
    return http("control-payment-error")
      .post(dependencyBase + "/control")
      .header("Content-Type", "application/json")
      .body(StringBody("{\"paymentMode\":\"ERROR\",\"errorRate\":1.0,\"delayMs\":0}"))
      .check(status().is(200));
  }

  static HttpRequestActionBuilder checkout(String name, String clientBase) {
    return http(name)
      .get(clientBase + "/checkout")
      .header("Accept", "application/json")
      .check(status().in(200, 502, 503));
  }

  static HttpRequestActionBuilder browse(String name, String clientBase) {
    return http(name)
      .get(clientBase + "/browse")
      .header("Accept", "application/json")
      .check(status().in(200, 502, 503));
  }
}
