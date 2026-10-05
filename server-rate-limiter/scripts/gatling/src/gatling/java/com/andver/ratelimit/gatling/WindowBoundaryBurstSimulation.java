package com.andver.ratelimit.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * One user, one key. A full quota near the end of a 1s window, then the same
 * quota just after the boundary.
 * Fixed window allows both. Sliding window still sees the first batch.
 * Token bucket (burst == limit) has only the tokens refilled during the gap.
 *
 * <pre>
 * ./gradlew :server-rate-limiter:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.ratelimit.gatling.WindowBoundaryBurstSimulation --non-interactive
 * </pre>
 */
public class WindowBoundaryBurstSimulation extends Simulation {

  private ScenarioBuilder boundary(String name, String base) {
    String user = "boundary-" + name;
    return scenario("boundary-" + name)
      .repeat(LoadConfig.cycles()).on(
        exec(session -> session.set("pauseMs", millisUntil(550)))
          .pause(session -> Duration.ofMillis(session.getLong("pauseMs")))
          .repeat(LoadConfig.catalogLimit()).on(
            exec(Api.catalog(name + "-early", base, user))
          )
          .exec(session -> session.set("pauseMs", millisUntil(40)))
          .pause(session -> Duration.ofMillis(session.getLong("pauseMs")))
          .repeat(LoadConfig.catalogLimit()).on(
            exec(Api.catalog(name + "-late", base, user))
          )
      );
  }

  {
    setUp(
      boundary("fixed", LoadConfig.fixedUrl()).injectOpen(atOnceUsers(1)),
      boundary("sliding", LoadConfig.slidingUrl()).injectOpen(atOnceUsers(1)),
      boundary("bucket", LoadConfig.bucketUrl()).injectOpen(atOnceUsers(1))
    )
      .protocols(http.baseUrl(LoadConfig.fixedUrl()).shareConnections())
      .maxDuration(Duration.ofSeconds(LoadConfig.cycles() * 3L + 20))
      .assertions(global().successfulRequests().percent().gt(99.0));
  }

  /** Milliseconds until the clock is {@code targetMs} into the current or next second. */
  private static long millisUntil(int targetMs) {
    long into = System.currentTimeMillis() % 1000;
    long delta = targetMs - into;
    if (delta < 0) {
      delta += 1000;
    }
    return delta;
  }
}
