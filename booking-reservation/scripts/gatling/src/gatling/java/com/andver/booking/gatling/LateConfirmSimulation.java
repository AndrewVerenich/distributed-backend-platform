package com.andver.booking.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;

/**
 * A holds → slow payment → B takes seat after TTL → confirm A compensates, confirm B sells.
 */
public class LateConfirmSimulation extends Simulation {

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.bookingUrl())
    .acceptHeader("application/json")
    .contentTypeHeader("application/json");

  long ttl = LoadConfig.holdTtlMs();

  ScenarioBuilder scn = scenario("late-confirm")
    .exec(http("reset").post("/admin/venues/last-seat/reset").check(status().is(200)))
    .exec(session -> session
      .set("userA", "late-a-" + UUID.randomUUID())
      .set("userB", "late-b-" + UUID.randomUUID())
      .set("keyHoldA", UUID.randomUUID().toString())
      .set("keyHoldB", UUID.randomUUID().toString())
      .set("keyConfA", UUID.randomUUID().toString())
      .set("keyConfB", UUID.randomUUID().toString())
    )
    .exec(
      http("hold-a")
        .post("/holds")
        .header("Idempotency-Key", session -> session.getString("keyHoldA"))
        .header("Idempotency-Key-Owner", session -> session.getString("userA"))
        .body(StringBody(session ->
          "{\"seatId\":\"" + LoadConfig.seatId() + "\",\"userId\":\"" + session.getString("userA") + "\"}"
        ))
        .check(status().is(200))
        .check(jsonPath("$.reservationId").saveAs("resA"))
    )
    .exec(
      http("slow-pay-a")
        .post("/payments")
        .body(StringBody(session ->
          "{\"reservationId\":\"" + session.getString("resA") + "\",\"delayMs\":" + (ttl + 2000) + "}"
        ))
        .check(status().is(200))
    )
    .pause(Duration.ofMillis(ttl + 1500))
    .exec(
      http("hold-b")
        .post("/holds")
        .header("Idempotency-Key", session -> session.getString("keyHoldB"))
        .header("Idempotency-Key-Owner", session -> session.getString("userB"))
        .body(StringBody(session ->
          "{\"seatId\":\"" + LoadConfig.seatId() + "\",\"userId\":\"" + session.getString("userB") + "\"}"
        ))
        .check(status().is(200))
        .check(jsonPath("$.reservationId").saveAs("resB"))
    )
    .exec(
      http("confirm-a-late")
        .post(session -> "/reservations/" + session.getString("resA") + "/confirm")
        .header("Idempotency-Key", session -> session.getString("keyConfA"))
        .header("Idempotency-Key-Owner", session -> session.getString("userA"))
        .check(status().is(409))
        .check(jsonPath("$.compensated").is("true"))
    )
    .exec(
      http("pay-b")
        .post("/payments")
        .body(StringBody(session ->
          "{\"reservationId\":\"" + session.getString("resB") + "\",\"delayMs\":0}"
        ))
        .check(status().is(200))
    )
    .exec(
      http("confirm-b")
        .post(session -> "/reservations/" + session.getString("resB") + "/confirm")
        .header("Idempotency-Key", session -> session.getString("keyConfB"))
        .header("Idempotency-Key-Owner", session -> session.getString("userB"))
        .check(status().is(200))
        .check(jsonPath("$.status").is("confirmed"))
    );

  {
    setUp(scn.injectOpen(atOnceUsers(1))).protocols(httpProtocol);
  }
}
