package com.andver.clientratelimit.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * Steady catalog traffic (upstream 20/s) through both clients.
 *
 * <pre>
 * ./gradlew :client-rate-limiter:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.clientratelimit.gatling.CatalogSteadySimulation --non-interactive
 * </pre>
 */
public class CatalogSteadySimulation extends Simulation {

  private ScenarioBuilder proxy(String name, String base) {
    return scenario(name)
      .exec(Api.catalog(name + "-catalog", base));
  }

  {
    Duration duration = Duration.ofSeconds(LoadConfig.durationSeconds());
    setUp(
      proxy("smart", LoadConfig.smartUrl())
        .injectOpen(constantUsersPerSec(LoadConfig.rps()).during(duration)),
      proxy("dumb", LoadConfig.dumbUrl())
        .injectOpen(constantUsersPerSec(LoadConfig.rps()).during(duration))
    )
      .protocols(http.shareConnections())
      .maxDuration(duration.plusSeconds(30))
      .assertions(global().successfulRequests().percent().gt(99.0));
  }
}
