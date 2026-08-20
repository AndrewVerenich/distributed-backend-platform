package com.andver.push.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;

/**
 * Long-poll load: publish → GET /poll/updates (hold until event or timeout).
 *
 * <pre>
 * ./gradlew gatlingRun-PollLoadSimulation -DUSERS=1000
 * </pre>
 */
public class PollLoadSimulation extends Simulation {

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.baseUrl())
    .acceptHeader("application/json")
    .shareConnections();

  ScenarioBuilder scn = scenario("poll-load")
    .exec(session -> session
      .set("clientId", "gatling-poll-" + UUID.randomUUID())
      .set("since", "0")
    )
    .exec(
      http("publish")
        .post(LoadConfig.producerUrl() + "/events")
        .header("Content-Type", "application/json")
        .body(StringBody(session ->
          "{\"clientId\":\"" + session.getString("clientId")
            + "\",\"type\":\"gatling.poll\",\"payload\":{\"at\":"
            + System.currentTimeMillis() + "}}"
        ))
        .check(status().in(200, 202))
    )
    .exec(
      http("long-poll")
        .get(session ->
          "/poll/updates?clientId=" + session.getString("clientId")
            + "&since=" + session.getString("since")
        )
        .requestTimeout(Duration.ofSeconds(35))
        .check(status().is(200))
        .check(jsonPath("$[*].eventId").findAll().optional().saveAs("eventIds"))
    )
    .pause(Duration.ofMillis(500));

  {
    setUp(
      scn.injectOpen(
        rampUsers(LoadConfig.users()).during(Duration.ofSeconds(LoadConfig.durationSeconds()))
      )
    )
      .protocols(httpProtocol)
      .assertions(
        global().successfulRequests().percent().gt(90.0)
      );
  }
}
