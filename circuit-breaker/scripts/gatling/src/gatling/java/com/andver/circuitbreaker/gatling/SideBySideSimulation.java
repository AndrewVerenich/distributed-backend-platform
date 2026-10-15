package com.andver.circuitbreaker.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * Same slow-payment workload against dumb and guarded clients in parallel.
 * Open Grafana while this runs: browse p99 diverges, payment breaker state flips on guarded.
 *
 * <pre>
 * ./gradlew :circuit-breaker:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.circuitbreaker.gatling.SideBySideSimulation --non-interactive
 * </pre>
 */
public class SideBySideSimulation extends Simulation {

  {
    Duration duration = Duration.ofSeconds(LoadConfig.durationSeconds());

    ScenarioBuilder control =
      scenario("set-payment-slow")
        .exec(Api.setPaymentSlow(LoadConfig.dependencyUrl(), LoadConfig.paymentDelayMs()));

    ScenarioBuilder dumbCheckout =
      scenario("dumb-checkout").exec(Api.checkout("dumb-checkout", LoadConfig.dumbUrl()));
    ScenarioBuilder dumbBrowse =
      scenario("dumb-browse").exec(Api.browse("dumb-browse", LoadConfig.dumbUrl()));
    ScenarioBuilder guardedCheckout =
      scenario("guarded-checkout").exec(Api.checkout("guarded-checkout", LoadConfig.guardedUrl()));
    ScenarioBuilder guardedBrowse =
      scenario("guarded-browse").exec(Api.browse("guarded-browse", LoadConfig.guardedUrl()));

    setUp(
      control.injectOpen(atOnceUsers(1)).andThen(
        dumbCheckout.injectOpen(constantUsersPerSec(LoadConfig.checkoutRps()).during(duration)),
        dumbBrowse.injectOpen(constantUsersPerSec(LoadConfig.browseRps()).during(duration)),
        guardedCheckout.injectOpen(constantUsersPerSec(LoadConfig.checkoutRps()).during(duration)),
        guardedBrowse.injectOpen(constantUsersPerSec(LoadConfig.browseRps()).during(duration))
      )
    )
      .protocols(http.shareConnections())
      .maxDuration(duration.plusSeconds(30));
  }
}
