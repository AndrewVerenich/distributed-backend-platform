package com.andver.webhook.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.UUID;

/**
 * Many parallel POSTs with the same eventId → exactly one insert (others 200 duplicate).
 */
public class AcceptIdempotencySimulation extends Simulation {
  private final String eventId = "evt-race-" + UUID.randomUUID();

  HttpProtocolBuilder httpProtocol = http.baseUrl(LoadConfig.API_URL)
      .contentTypeHeader("application/json");

  ScenarioBuilder scn = scenario("accept-idempotency")
      .exec(
          http("accept")
              .post("/internal/events")
              .body(StringBody(
                  "{\"eventId\":\"" + eventId + "\","
                      + "\"tenantId\":\"ten_demo\","
                      + "\"eventType\":\"order.paid\","
                      + "\"partitionKey\":\"order-race\","
                      + "\"body\":{\"n\":1}}"
              ))
              .check(status().in(200, 202))
      );

  {
    setUp(scn.injectOpen(atOnceUsers(LoadConfig.USERS))).protocols(httpProtocol);
  }
}
