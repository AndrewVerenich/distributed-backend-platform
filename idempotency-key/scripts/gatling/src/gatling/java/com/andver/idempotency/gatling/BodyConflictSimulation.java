package com.andver.idempotency.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.util.UUID;

/**
 * Reuses a completed key with a different JSON body and expects HTTP 422.
 */
public class BodyConflictSimulation extends Simulation {

  {
    String key = UUID.randomUUID().toString();
    String owner = LoadConfig.owner();

    ScenarioBuilder conflict =
      scenario("body-conflict")
        .exec(
          http("first")
            .post("/charges")
            .header("Idempotency-Key", key)
            .header("Idempotency-Key-Owner", owner)
            .header("Content-Type", "application/json")
            .body(StringBody("{\"amount\":1}"))
            .check(status().is(200))
        )
        .exec(
          http("different-body")
            .post("/charges")
            .header("Idempotency-Key", key)
            .header("Idempotency-Key-Owner", owner)
            .header("Content-Type", "application/json")
            .body(StringBody("{\"amount\":2}"))
            .check(status().is(422))
        );

    setUp(conflict.injectOpen(atOnceUsers(1)))
      .protocols(http.baseUrl(LoadConfig.chargeUrl()));
  }
}
