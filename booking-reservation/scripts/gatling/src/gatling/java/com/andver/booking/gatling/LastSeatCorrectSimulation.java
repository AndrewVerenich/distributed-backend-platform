package com.andver.booking.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * N parallel correct holds on one seat → exactly one 200, rest 409.
 *
 * <pre>
 * ./gradlew :booking-reservation:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.booking.gatling.LastSeatCorrectSimulation --non-interactive
 * </pre>
 */
public class LastSeatCorrectSimulation extends Simulation {

  private static final AtomicInteger OK = new AtomicInteger();
  private static final AtomicInteger CONFLICT = new AtomicInteger();

  HttpProtocolBuilder httpProtocol = http
    .baseUrl(LoadConfig.bookingUrl())
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .shareConnections();

  ScenarioBuilder reset = scenario("reset-venue")
    .exec(
      http("reset")
        .post("/admin/venues/last-seat/reset")
        .check(status().is(200))
    );

  ScenarioBuilder race = scenario("last-seat-correct")
    .exec(session -> session
      .set("userId", "u-" + UUID.randomUUID())
      .set("idemKey", UUID.randomUUID().toString())
    )
    .exec(
      http("hold")
        .post("/holds")
        .header("Idempotency-Key", session -> session.getString("idemKey"))
        .header("Idempotency-Key-Owner", session -> session.getString("userId"))
        .body(StringBody(session ->
          "{\"seatId\":\"" + LoadConfig.seatId() + "\",\"userId\":\"" + session.getString("userId") + "\"}"
        ))
        .check(status().in(200, 409).saveAs("statusCode"))
    )
    .exec(session -> {
      int code = session.getInt("statusCode");
      if (code == 200) {
        OK.incrementAndGet();
      } else {
        CONFLICT.incrementAndGet();
      }
      return session;
    });

  {
    setUp(
      reset.injectOpen(atOnceUsers(1))
        .andThen(race.injectOpen(atOnceUsers(LoadConfig.users())))
    ).protocols(httpProtocol)
      .assertions(
        global().failedRequests().count().is(0L)
      );
  }

  @Override
  public void after() {
    System.out.println("=== LastSeatCorrect: ok=" + OK.get() + " conflict=" + CONFLICT.get() + " ===");
    if (OK.get() != 1) {
      throw new IllegalStateException("Expected exactly one successful hold, got " + OK.get());
    }
  }
}
