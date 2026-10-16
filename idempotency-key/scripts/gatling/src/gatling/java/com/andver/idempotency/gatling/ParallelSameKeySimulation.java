package com.andver.idempotency.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.util.UUID;

/**
 * Many parallel POSTs share one Idempotency-Key. Exactly one charge must be created;
 * others are 409 then eventually replay, or direct replay of the completed response.
 *
 * <pre>
 * ./gradlew :idempotency-key:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.idempotency.gatling.ParallelSameKeySimulation --non-interactive
 * </pre>
 */
public class ParallelSameKeySimulation extends Simulation {

  {
    String key = UUID.randomUUID().toString();
    String owner = LoadConfig.owner();
    long amount = LoadConfig.amount();

    ScenarioBuilder sameKey =
      scenario("parallel-same-key")
        .exec(
          http("charge")
            .post("/charges")
            .header("Idempotency-Key", key)
            .header("Idempotency-Key-Owner", owner)
            .header("Content-Type", "application/json")
            .body(StringBody("{\"amount\":" + amount + "}"))
            .check(status().in(200, 409))
        );

    setUp(sameKey.injectOpen(atOnceUsers(LoadConfig.users())))
      .protocols(http.baseUrl(LoadConfig.chargeUrl()).shareConnections())
      .assertions(global().successfulRequests().percent().gt(99.0));
  }
}
