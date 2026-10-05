package com.andver.ratelimit.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * Flat load above the catalog quota (default 50 rps vs limit 20/s).
 * All three algorithms should allow about the same sustained rate.
 *
 * <pre>
 * ./gradlew :server-rate-limiter:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.ratelimit.gatling.SteadyTrafficSimulation --non-interactive
 * </pre>
 */
public class SteadyTrafficSimulation extends Simulation {

  private ScenarioBuilder steady(String name, String base) {
    return scenario("steady-" + name)
      .exec(Api.catalog(name + "-catalog", base, "steady-user"));
  }

  {
    Duration duration = Duration.ofSeconds(LoadConfig.durationSeconds());
    setUp(
      steady("fixed", LoadConfig.fixedUrl())
        .injectOpen(constantUsersPerSec(LoadConfig.rps()).during(duration)),
      steady("sliding", LoadConfig.slidingUrl())
        .injectOpen(constantUsersPerSec(LoadConfig.rps()).during(duration)),
      steady("bucket", LoadConfig.bucketUrl())
        .injectOpen(constantUsersPerSec(LoadConfig.rps()).during(duration))
    )
      .protocols(http.baseUrl(LoadConfig.fixedUrl()).shareConnections())
      .maxDuration(duration.plusSeconds(15))
      .assertions(global().successfulRequests().percent().gt(99.0));
  }
}
