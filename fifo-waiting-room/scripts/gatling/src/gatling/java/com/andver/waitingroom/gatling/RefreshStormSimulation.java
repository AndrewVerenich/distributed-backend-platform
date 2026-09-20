package com.andver.waitingroom.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Refresh storm: many concurrent join/status for the same visitorId must not move FIFO position.
 *
 * <pre>
 * ./gradlew :fifo-waiting-room:scripts:gatling:gatlingRun \\
 *   --simulation=com.andver.waitingroom.gatling.RefreshStormSimulation -DUSERS=50
 * </pre>
 */
public class RefreshStormSimulation extends Simulation {

  private static final String VISITOR_ID = System.getProperty("VISITOR_ID", "refresh-storm-visitor");
  private static final AtomicLong BASE_POSITION = new AtomicLong(-1);

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.waitingRoomUrl())
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .shareConnections();

  ScenarioBuilder init = scenario("refresh-init")
    .exec(
      http("initial-join")
        .post("/events/" + LoadConfig.eventId() + "/join")
        .body(StringBody("{\"visitorId\":\"" + VISITOR_ID + "\"}"))
        .check(status().is(200))
        .check(jsonPath("$.position").ofLong().saveAs("position"))
    )
    .exec(session -> {
      BASE_POSITION.set(session.getLong("position"));
      return session;
    });

  ScenarioBuilder storm = scenario("refresh-storm")
    .exec(
      http("refresh-join")
        .post("/events/" + LoadConfig.eventId() + "/join")
        .body(StringBody("{\"visitorId\":\"" + VISITOR_ID + "\"}"))
        .check(status().is(200))
        .check(jsonPath("$.position").ofLong().saveAs("joinPosition"))
        .check(jsonPath("$.status").saveAs("joinStatus"))
    )
    .exec(session -> {
      String status = session.getString("joinStatus");
      long pos = session.getLong("joinPosition");
      long base = BASE_POSITION.get();
      if ("WAITING".equals(status) && pos != base) {
        throw new RuntimeException("join moved position: " + pos + " != " + base);
      }
      return session;
    })
    .exec(
      http("refresh-status")
        .get("/events/" + LoadConfig.eventId() + "/status/" + VISITOR_ID)
        .check(status().is(200))
        .check(jsonPath("$.position").ofLong().saveAs("statusPosition"))
        .check(jsonPath("$.status").saveAs("statusValue"))
    )
    .exec(session -> {
      String status = session.getString("statusValue");
      long pos = session.getLong("statusPosition");
      long base = BASE_POSITION.get();
      if ("WAITING".equals(status) && pos != base) {
        throw new RuntimeException("status moved position: " + pos + " != " + base);
      }
      return session;
    })
    .pause(Duration.ofMillis(200));

  {
    double rate = Math.max(1.0, LoadConfig.users() / 10.0);
    setUp(
      init.injectOpen(atOnceUsers(1))
        .andThen(
          storm.injectOpen(
            constantUsersPerSec(rate).during(Duration.ofSeconds(LoadConfig.durationSeconds()))
          )
        )
    )
      .protocols(httpProtocol)
      .assertions(global().successfulRequests().percent().gt(95.0));
  }
}
