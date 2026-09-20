package com.andver.waitingroom.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

/**
 * Direct path: checkout without opaque ticket must be rejected (401).
 *
 * <pre>
 * ./gradlew :fifo-waiting-room:scripts:gatling:gatlingRun \\
 *   --simulation=com.andver.waitingroom.gatling.DirectCheckoutSimulation -DUSERS=80
 * </pre>
 */
public class DirectCheckoutSimulation extends Simulation {

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.checkoutUrl())
    .acceptHeader("application/json")
    .shareConnections();

  ScenarioBuilder scn = scenario("direct-checkout")
    .exec(
      http("checkout-without-ticket")
        .post("/checkout")
        .check(status().is(401))
    )
    .pause(Duration.ofMillis(100));

  {
    setUp(
      scn.injectOpen(
        constantUsersPerSec(Math.max(1.0, LoadConfig.users() / 5.0))
          .during(Duration.ofSeconds(LoadConfig.durationSeconds()))
      )
    )
      .protocols(httpProtocol)
      .assertions(global().successfulRequests().percent().gt(99.0));
  }
}
