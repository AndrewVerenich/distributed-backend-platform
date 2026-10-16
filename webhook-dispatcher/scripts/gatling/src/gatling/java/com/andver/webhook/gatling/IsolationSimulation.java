package com.andver.webhook.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Publishes order.isolated events (ok + slow webhooks). Accept stays fast;
 * isolation is verified via delivery statuses after the run.
 */
public class IsolationSimulation extends Simulation {
  private final AtomicInteger seq = new AtomicInteger();

  HttpProtocolBuilder httpProtocol = http.baseUrl(LoadConfig.API_URL)
      .contentTypeHeader("application/json");

  ScenarioBuilder scn = scenario("isolation-accept")
      .exec(session -> session.set("eventId", "evt-iso-" + seq.incrementAndGet() + "-" + UUID.randomUUID()))
      .exec(
          http("accept-isolated")
              .post("/internal/events")
              .body(StringBody(session ->
                  "{\"eventId\":\"" + session.getString("eventId") + "\","
                      + "\"tenantId\":\"ten_demo\","
                      + "\"eventType\":\"order.isolated\","
                      + "\"partitionKey\":\"order-" + session.getString("eventId") + "\","
                      + "\"body\":{\"iso\":true}}"
              ))
              .check(status().is(202))
              .check(responseTimeInMillis().lte(500))
      );

  {
    setUp(scn.injectOpen(rampUsers(LoadConfig.USERS).during(10))).protocols(httpProtocol);
  }
}
