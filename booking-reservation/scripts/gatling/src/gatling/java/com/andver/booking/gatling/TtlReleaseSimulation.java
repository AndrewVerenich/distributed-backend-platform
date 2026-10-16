package com.andver.booking.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;

/**
 * Hold → wait past TTL → second hold succeeds on same seat.
 */
public class TtlReleaseSimulation extends Simulation {

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.bookingUrl())
    .acceptHeader("application/json")
    .contentTypeHeader("application/json");

  ScenarioBuilder scn = scenario("ttl-release")
    .exec(http("reset").post("/admin/venues/last-seat/reset").check(status().is(200)))
    .exec(session -> session
      .set("userA", "ttl-a-" + UUID.randomUUID())
      .set("keyA", UUID.randomUUID().toString())
      .set("userB", "ttl-b-" + UUID.randomUUID())
      .set("keyB", UUID.randomUUID().toString())
    )
    .exec(
      http("hold-a")
        .post("/holds")
        .header("Idempotency-Key", session -> session.getString("keyA"))
        .header("Idempotency-Key-Owner", session -> session.getString("userA"))
        .body(StringBody(session ->
          "{\"seatId\":\"" + LoadConfig.seatId() + "\",\"userId\":\"" + session.getString("userA") + "\"}"
        ))
        .check(status().is(200))
        .check(jsonPath("$.reservationId").saveAs("resA"))
    )
    .pause(Duration.ofMillis(LoadConfig.holdTtlMs() + 1500))
    .exec(
      http("hold-b")
        .post("/holds")
        .header("Idempotency-Key", session -> session.getString("keyB"))
        .header("Idempotency-Key-Owner", session -> session.getString("userB"))
        .body(StringBody(session ->
          "{\"seatId\":\"" + LoadConfig.seatId() + "\",\"userId\":\"" + session.getString("userB") + "\"}"
        ))
        .check(status().is(200))
    );

  {
    setUp(scn.injectOpen(atOnceUsers(1))).protocols(httpProtocol);
  }
}
