package com.andver.ratelimit.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;
import java.util.UUID;

/**
 * Many distinct X-User-Id values should spread across Redis shards (X-RateLimit-Shard).
 * One sticky user stays on a single shard.
 *
 * <pre>
 * ./gradlew :server-rate-limiter:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.ratelimit.gatling.ShardSpreadSimulation --non-interactive
 * </pre>
 */
public class ShardSpreadSimulation extends Simulation {

  ScenarioBuilder manyUsers = scenario("many-users-spread")
    .exec(session -> session.set("userId", "u-" + UUID.randomUUID()))
    .exec(Api.catalog("catalog-many", LoadConfig.bucketUrl(), "#{userId}"));

  ScenarioBuilder stickyUser = scenario("sticky-user")
    .exec(Api.catalog("catalog-sticky", LoadConfig.bucketUrl(), "sticky-alice"));

  {
    Duration duration = Duration.ofSeconds(LoadConfig.durationSeconds());
    setUp(
      manyUsers.injectOpen(constantUsersPerSec(LoadConfig.rps()).during(duration)),
      stickyUser.injectOpen(constantUsersPerSec(10).during(duration))
    )
      .protocols(http.acceptHeader("application/json").shareConnections())
      .maxDuration(duration.plusSeconds(15))
      .assertions(global().successfulRequests().percent().gt(99.0));
  }
}
