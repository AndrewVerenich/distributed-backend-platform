package com.andver.push.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;

/**
 * Side-by-side SSE vs long poll under the same user ramp.
 *
 * <pre>
 * ./gradlew gatlingRun-CompareTransportsSimulation -DUSERS=200
 * </pre>
 */
public class CompareTransportsSimulation extends Simulation {

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.baseUrl())
    .shareConnections();

  ScenarioBuilder sseScn = scenario("compare-sse")
    .exec(session -> session.set("clientId", "cmp-sse-" + UUID.randomUUID()))
    .exec(
      http("publish-sse")
        .post(LoadConfig.producerUrl() + "/events")
        .header("Content-Type", "application/json")
        .body(StringBody(session ->
          "{\"clientId\":\"" + session.getString("clientId")
            + "\",\"type\":\"cmp.sse\",\"payload\":{}}"
        ))
        .check(status().in(200, 202))
    )
    .exec(
      sse("sse-connect")
        .get(session -> "/sse/stream?clientId=" + session.getString("clientId"))
        .await(8)
        .on(sse.checkMessage("msg").check(regex("eventId").exists()))
    )
    .pause(Duration.ofSeconds(1));

  ScenarioBuilder pollScn = scenario("compare-poll")
    .exec(session -> session.set("clientId", "cmp-poll-" + UUID.randomUUID()))
    .exec(
      http("publish-poll")
        .post(LoadConfig.producerUrl() + "/events")
        .header("Content-Type", "application/json")
        .body(StringBody(session ->
          "{\"clientId\":\"" + session.getString("clientId")
            + "\",\"type\":\"cmp.poll\",\"payload\":{}}"
        ))
        .check(status().in(200, 202))
    )
    .exec(
      http("long-poll")
        .get(session ->
          "/poll/updates?clientId=" + session.getString("clientId") + "&since=0"
        )
        .requestTimeout(Duration.ofSeconds(35))
        .check(status().is(200))
    )
    .pause(Duration.ofMillis(500));

  {
    int users = LoadConfig.users();
    setUp(
      sseScn.injectOpen(rampUsers(users).during(Duration.ofSeconds(20))),
      pollScn.injectOpen(rampUsers(users).during(Duration.ofSeconds(20)))
    ).protocols(httpProtocol);
  }
}
