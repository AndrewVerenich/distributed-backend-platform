package com.andver.circuitbreaker.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * Separate pools + per-command breakers. After payment opens, checkout fails fast (503);
 * browse stays on the healthy catalog breaker/pool.
 *
 * <pre>
 * ./gradlew :circuit-breaker:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.circuitbreaker.gatling.IsolatedWithBreakerSimulation --non-interactive
 * </pre>
 */
public class IsolatedWithBreakerSimulation extends Simulation {

  {
    Duration duration = Duration.ofSeconds(LoadConfig.durationSeconds());

    ScenarioBuilder control =
      scenario("set-payment-slow")
        .exec(Api.setPaymentSlow(LoadConfig.dependencyUrl(), LoadConfig.paymentDelayMs()));

    ScenarioBuilder checkout =
      scenario("guarded-checkout")
        .exec(Api.checkout("guarded-checkout", LoadConfig.guardedUrl()));

    ScenarioBuilder browse =
      scenario("guarded-browse")
        .exec(Api.browse("guarded-browse", LoadConfig.guardedUrl()));

    setUp(
      control.injectOpen(atOnceUsers(1)).andThen(
        checkout.injectOpen(constantUsersPerSec(LoadConfig.checkoutRps()).during(duration)),
        browse.injectOpen(constantUsersPerSec(LoadConfig.browseRps()).during(duration))
      )
    )
      .protocols(http.shareConnections())
      .maxDuration(duration.plusSeconds(30));
  }
}
