package com.andver.singleflight.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Many distinct keys — coalesce ratio should stay near zero.
 *
 * <pre>
 * ./gradlew :singleflight:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.singleflight.gatling.UniqueKeysSimulation --non-interactive
 * </pre>
 */
public class UniqueKeysSimulation extends Simulation {

  private final AtomicInteger seq = new AtomicInteger();

  private Iterator<Map<String, Object>> ids() {
    return Stream.generate(() -> Map.<String, Object>of("id", "sku-" + seq.incrementAndGet())).iterator();
  }

  {
    ScenarioBuilder unique = scenario("unique-keys")
      .feed(ids())
      .exec(
        http("distributed-unique")
          .get(LoadConfig.distributedUrl() + "/products/#{id}")
          .check(io.gatling.javaapi.http.HttpDsl.status().is(200))
      );

    setUp(unique.injectOpen(rampUsers(40).during(Duration.ofSeconds(2)), constantUsersPerSec(40).during(8)))
      .protocols(http.baseUrl(LoadConfig.distributedUrl()).shareConnections())
      .assertions(global().successfulRequests().percent().gt(95.0));
  }
}
