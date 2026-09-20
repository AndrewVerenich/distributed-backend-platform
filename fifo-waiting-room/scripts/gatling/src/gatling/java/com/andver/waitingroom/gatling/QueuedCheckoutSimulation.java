package com.andver.waitingroom.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.UUID;

/**
 * Queued path: join → poll until ticket → checkout 200 → ticket replay 401.
 *
 * <pre>
 * ./gradlew :fifo-waiting-room:scripts:gatling:gatlingRun \\
 *   --simulation=com.andver.waitingroom.gatling.QueuedCheckoutSimulation -DUSERS=80
 * </pre>
 */
public class QueuedCheckoutSimulation extends Simulation {

  HttpProtocolBuilder waitingRoom = http
    .baseUrl(LoadConfig.waitingRoomUrl())
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .shareConnections();

  ScenarioBuilder scn = scenario("queued-checkout")
    .exec(session -> session
      .set("visitorId", "gatling-queued-" + UUID.randomUUID())
      .set("qStatus", "WAITING")
      .set("ticket", "")
      .set("polls", 0)
    )
    .exec(
      http("join")
        .post("/events/" + LoadConfig.eventId() + "/join")
        .body(StringBody(session -> "{\"visitorId\":\"" + session.getString("visitorId") + "\"}"))
        .check(status().is(200))
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
            .check(jsonPath("$.ticket").optional().saveAs("ticket"))
        )
        .pause(Duration.ofSeconds(1))
    )
    .exec(session -> {
      String ticket = session.getString("ticket");
      if (ticket == null || ticket.isBlank()) {
        throw new RuntimeException("no ticket for " + session.getString("visitorId"));
      }
      return session;
    })
    .exec(
      http("checkout")
        .post(LoadConfig.checkoutUrl() + "/checkout")
        .header("Authorization", session -> "Ticket " + session.getString("ticket"))
        .check(status().is(200))
    )
    .exec(
      http("checkout-replay")
        .post(LoadConfig.checkoutUrl() + "/checkout")
        .header("Authorization", session -> "Ticket " + session.getString("ticket"))
        .check(status().is(401))
    );

  {
    setUp(
      scn.injectOpen(
        rampUsers(LoadConfig.users()).during(Duration.ofSeconds(Math.min(30, LoadConfig.durationSeconds())))
      )
    )
      .protocols(waitingRoom)
      .maxDuration(Duration.ofSeconds(LoadConfig.durationSeconds() + LoadConfig.maxPolls() + 60L))
      .assertions(global().successfulRequests().percent().gt(90.0));
  }
}
