package com.andver.push.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;

/**
 * SSE load: publish event, open EventSource stream, wait for first message.
 *
 * <pre>
 * ./gradlew gatlingRun-SseLoadSimulation -DUSERS=1000
 * </pre>
 */
public class SseLoadSimulation extends Simulation {

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.baseUrl())
    .acceptHeader("text/event-stream")
    .shareConnections();

  ScenarioBuilder scn = scenario("sse-load")
    .exec(session -> session.set("clientId", "gatling-sse-" + UUID.randomUUID()))
    .exec(
      http("publish")
        .post(LoadConfig.producerUrl() + "/events")
        .header("Content-Type", "application/json")
        .body(StringBody(session ->
          "{\"clientId\":\"" + session.getString("clientId")
            + "\",\"type\":\"gatling.sse\",\"payload\":{\"at\":"
            + System.currentTimeMillis() + "}}"
        ))
        .check(status().in(200, 202))
    )
    .exec(
      sse("sse-connect")
        .get(session -> "/sse/stream?clientId=" + session.getString("clientId"))
        .await(10)
        .on(sse.checkMessage("first-event").check(regex("eventId").exists()))
    )
    .pause(Duration.ofSeconds(1));

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
