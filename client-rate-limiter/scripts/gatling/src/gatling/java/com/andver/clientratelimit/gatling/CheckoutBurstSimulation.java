package com.andver.clientratelimit.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * Concurrent burst on /proxy/checkout (upstream limit 5/s). Shows smart pacing vs dumb 429 storm.
 *
 * <pre>
 * ./gradlew :client-rate-limiter:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.clientratelimit.gatling.CheckoutBurstSimulation --non-interactive \
 *   -DBURST_USERS=60
 * </pre>
 */
public class CheckoutBurstSimulation extends Simulation {

  {
    setUp(
      scenario("smart-burst")
        .exec(Api.checkout("smart-checkout", LoadConfig.smartUrl()))
        .injectOpen(atOnceUsers(LoadConfig.burstUsers())),
      scenario("dumb-burst")
        .exec(Api.checkout("dumb-checkout", LoadConfig.dumbUrl()))
        .injectOpen(atOnceUsers(LoadConfig.burstUsers()))
    )
      .protocols(http.shareConnections())
      .maxDuration(Duration.ofSeconds(60))
      .assertions(global().successfulRequests().percent().gt(99.0));
  }
}
