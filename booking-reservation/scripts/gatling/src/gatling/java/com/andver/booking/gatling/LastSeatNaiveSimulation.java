package com.andver.booking.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Naive read-then-write race — expect oversell (ok > 1).
 */
public class LastSeatNaiveSimulation extends Simulation {

  private static final AtomicInteger OK = new AtomicInteger();

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.bookingUrl())
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .shareConnections();

  ScenarioBuilder reset = scenario("reset-venue")
    .exec(http("reset").post("/admin/venues/last-seat/reset").check(status().is(200)));

  ScenarioBuilder race = scenario("last-seat-naive")
    .exec(session -> session.set("userId", "naive-" + UUID.randomUUID()))
    .exec(
      http("naive-hold")
        .post("/naive/holds")
        .body(StringBody(session ->
          "{\"seatId\":\"" + LoadConfig.seatId() + "\",\"userId\":\"" + session.getString("userId") + "\"}"
        ))
        .check(status().in(200, 409).saveAs("statusCode"))
    )
    .exec(session -> {
      if (session.getInt("statusCode") == 200) {
        OK.incrementAndGet();
      }
      return session;
    });

  {
    setUp(
      reset.injectOpen(atOnceUsers(1))
        .andThen(race.injectOpen(atOnceUsers(LoadConfig.users())))
    ).protocols(httpProtocol);
  }

  @Override
  public void after() {
    System.out.println("=== LastSeatNaive: ok=" + OK.get() + " (oversell if > 1) ===");
  }
}
