package com.andver.singleflight.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * Burst one product id against off / local / distributed frontends.
 *
 * <pre>
 * ./gradlew :singleflight:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.singleflight.gatling.HotKeyBurstSimulation --non-interactive
 * </pre>
 *
 * After the run, compare upstream calls:
 * {@code curl http://localhost:8220/admin/calls} (reset before each mode if needed).
 */
public class HotKeyBurstSimulation extends Simulation {

  private ScenarioBuilder burst(String name, String base) {
    return scenario("burst-" + name)
      .exec(Api.product(name + "-product", base, LoadConfig.productId()));
  }

  {
    int users = LoadConfig.users();
    Duration duration = Duration.ofSeconds(LoadConfig.durationSeconds());

    setUp(
      burst("off", LoadConfig.offUrl())
        .injectOpen(rampUsers(users).during(Duration.ofSeconds(2)), constantUsersPerSec(users).during(duration)),
      burst("local", LoadConfig.localUrl())
        .injectOpen(rampUsers(users).during(Duration.ofSeconds(2)), constantUsersPerSec(users).during(duration)),
      burst("distributed", LoadConfig.distributedUrl())
        .injectOpen(rampUsers(users).during(Duration.ofSeconds(2)), constantUsersPerSec(users).during(duration))
    )
      .protocols(http.baseUrl(LoadConfig.offUrl()).shareConnections())
      .maxDuration(duration.plusSeconds(20))
      .assertions(global().successfulRequests().percent().gt(95.0));
  }
}
