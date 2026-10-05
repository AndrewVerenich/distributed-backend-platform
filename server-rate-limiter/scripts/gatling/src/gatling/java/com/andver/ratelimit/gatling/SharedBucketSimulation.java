package com.andver.ratelimit.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Two token-bucket replicas, one Redis key. The burst is cluster-wide, not per process.
 *
 * <pre>
 * ./gradlew :server-rate-limiter:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.ratelimit.gatling.SharedBucketSimulation --non-interactive \
 *   -DUSERS=80
 * </pre>
 */
public class SharedBucketSimulation extends Simulation {

  {
    setUp(
      scenario("shared-bucket")
        .exec(
          http("catalog")
            .get(session -> {
              String base = ThreadLocalRandom.current().nextBoolean()
                ? LoadConfig.bucketUrl()
                : LoadConfig.bucketUrl2();
              return base + "/api/catalog/sku-1";
            })
            .header("X-User-Id", "shared-user")
            .header("Accept", "application/json")
            .check(status().in(200, 429))
            .check(header("X-RateLimit-Limit").exists())
            .check(header("X-RateLimit-Remaining").exists())
        )
        .injectOpen(atOnceUsers(LoadConfig.users()))
    )
      .protocols(http.baseUrl(LoadConfig.bucketUrl()).shareConnections())
      .maxDuration(Duration.ofSeconds(30))
      .assertions(global().successfulRequests().percent().gt(99.0));
  }
}
