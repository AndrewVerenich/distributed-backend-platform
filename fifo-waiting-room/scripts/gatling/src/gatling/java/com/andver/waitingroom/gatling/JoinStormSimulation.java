package com.andver.waitingroom.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;

/**
 * Join storm: N users join the FIFO queue and poll until ADMITTED.
 *
 * <pre>
 * ./gradlew :fifo-waiting-room:scripts:gatling:gatlingRun \\
 *   --simulation=com.andver.waitingroom.gatling.JoinStormSimulation -DUSERS=100
 * </pre>
 */
public class JoinStormSimulation extends Simulation {

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.waitingRoomUrl())
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .shareConnections();

  ScenarioBuilder scn = scenario("join-storm")
    .exec(session -> session
      .set("visitorId", "gatling-join-" + UUID.randomUUID())
      .set("qStatus", "WAITING")
      .set("polls", 0)
    )
    .exec(
      http("join")
        .post("/events/" + LoadConfig.eventId() + "/join")
        .body(StringBody(session -> "{\"visitorId\":\"" + session.getString("visitorId") + "\"}"))
        .check(status().is(200))
        .check(jsonPath("$.position").ofLong().gt(0L))
    )
    .asLongAs(session ->
      !"ADMITTED".equals(session.getString("qStatus"))
        && session.getInt("polls") < LoadConfig.maxPolls()
    )
    .on(
      exec(session -> session.set("polls", session.getInt("polls") + 1))
        .exec(
          http("status")
            .get(session ->
              "/events/" + LoadConfig.eventId() + "/status/" + session.getString("visitorId")
            )
            .check(status().is(200))
            .check(jsonPath("$.status").saveAs("qStatus"))
        )
        .pause(Duration.ofSeconds(1))
    )
    .exec(session -> {
      if (!"ADMITTED".equals(session.getString("qStatus"))) {
        throw new RuntimeException("admit timeout for " + session.getString("visitorId"));
      }
      return session;
    });

  {
    setUp(
      scn.injectOpen(
        rampUsers(LoadConfig.users()).during(Duration.ofSeconds(Math.min(30, LoadConfig.durationSeconds())))
      )
    )
      .protocols(httpProtocol)
      .maxDuration(Duration.ofSeconds(LoadConfig.durationSeconds() + LoadConfig.maxPolls() + 30L))
      .assertions(global().successfulRequests().percent().gt(95.0));
  }
}
