package com.andver.clientratelimit.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * Side-by-side load through smart (header-aware) and dumb (fixed retry) clients on /proxy/checkout.
 * Fill Grafana: smart recovered vs dumb still rate_limited.
 *
 * <pre>
 * ./gradlew :client-rate-limiter:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.clientratelimit.gatling.SmartVsDumbSimulation --non-interactive
 * </pre>
 */
public class SmartVsDumbSimulation extends Simulation {

  private ScenarioBuilder proxy(String name, String base) {
    return scenario(name)
      .exec(Api.checkout(name + "-checkout", base));
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
      .maxDuration(duration.plusSeconds(45))
      .assertions(global().successfulRequests().percent().gt(95.0));
  }
}
