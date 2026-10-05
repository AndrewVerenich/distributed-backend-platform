package com.andver.ratelimit.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * Checkout quota is 5/s. Token bucket burst is 20, the other algorithms ignore burst.
 * Drain the bucket, stay quiet long enough to refill, then send a pack.
 * Token bucket lets the burst through. Fixed and sliding stay at 5.
 *
 * <pre>
 * ./gradlew :server-rate-limiter:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.ratelimit.gatling.SilenceThenBurstSimulation --non-interactive
 * </pre>
 */
public class SilenceThenBurstSimulation extends Simulation {

  private ScenarioBuilder silence(String name, String base) {
    String user = "silence-" + name;
    return scenario("silence-" + name)
      .repeat(30).on(exec(Api.checkout(name + "-drain", base, user)))
      .pause(Duration.ofSeconds(4))
      .repeat(25).on(exec(Api.checkout(name + "-burst", base, user)));
  }

  {
    setUp(
      silence("fixed", LoadConfig.fixedUrl()).injectOpen(atOnceUsers(1)),
      silence("sliding", LoadConfig.slidingUrl()).injectOpen(atOnceUsers(1)),
      silence("bucket", LoadConfig.bucketUrl()).injectOpen(atOnceUsers(1))
    )
      .protocols(http.baseUrl(LoadConfig.fixedUrl()).shareConnections())
      .maxDuration(Duration.ofSeconds(30))
      .assertions(global().successfulRequests().percent().gt(99.0));
  }
}
