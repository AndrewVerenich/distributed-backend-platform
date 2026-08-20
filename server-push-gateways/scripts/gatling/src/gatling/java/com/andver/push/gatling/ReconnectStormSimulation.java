package com.andver.push.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;

/**
 * Reconnect storm: seed event into replay buffer, then poll with since=0 (catch-up).
 *
 * <pre>
 * ./gradlew gatlingRun-ReconnectStormSimulation -DUSERS=500
 * </pre>
 */
public class ReconnectStormSimulation extends Simulation {

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.baseUrl())
    .acceptHeader("application/json")
    .shareConnections();

  ScenarioBuilder scn = scenario("reconnect-storm")
    .exec(session -> session.set("clientId", "gatling-reconnect-" + UUID.randomUUID()))
    .repeat(3)
    .on(
      exec(
        http("seed")
          .post(LoadConfig.producerUrl() + "/events")
          .header("Content-Type", "application/json")
          .body(StringBody(session ->
            "{\"clientId\":\"" + session.getString("clientId")
              + "\",\"type\":\"gatling.seed\",\"payload\":{\"n\":1}}"
          ))
          .check(status().in(200, 202))
      )
        .pause(Duration.ofMillis(200))
        .exec(
          http("reconnect-poll")
            .get(session ->
              "/poll/updates?clientId=" + session.getString("clientId") + "&since=0"
            )
            .requestTimeout(Duration.ofSeconds(10))
            .check(status().is(200))
            .check(jsonPath("$[0].eventId").exists())
        )
        .pause(Duration.ofMillis(100))
    );

  {
    setUp(
      scn.injectOpen(
        atOnceUsers(LoadConfig.users())
      )
    )
      .protocols(httpProtocol)
      .maxDuration(Duration.ofMinutes(2))
      .assertions(
        global().successfulRequests().percent().gt(85.0)
      );
  }
}
