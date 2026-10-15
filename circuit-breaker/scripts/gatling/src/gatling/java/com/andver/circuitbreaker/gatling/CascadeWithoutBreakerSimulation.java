package com.andver.circuitbreaker.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * Shared connection pool, no circuit breaker. Slow payment saturates the pool;
 * browse p99 collapses even though catalog is healthy.
 *
 * <pre>
 * ./gradlew :circuit-breaker:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.circuitbreaker.gatling.CascadeWithoutBreakerSimulation --non-interactive
 * </pre>
 */
public class CascadeWithoutBreakerSimulation extends Simulation {

  {
    Duration duration = Duration.ofSeconds(LoadConfig.durationSeconds());

    ScenarioBuilder control =
      scenario("set-payment-slow")
        .exec(Api.setPaymentSlow(LoadConfig.dependencyUrl(), LoadConfig.paymentDelayMs()));

    ScenarioBuilder checkout =
      scenario("dumb-checkout")
        .exec(Api.checkout("dumb-checkout", LoadConfig.dumbUrl()));

    ScenarioBuilder browse =
      scenario("dumb-browse")
        .exec(Api.browse("dumb-browse", LoadConfig.dumbUrl()));

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
