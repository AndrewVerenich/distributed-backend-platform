package com.andver.singleflight.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * Forces upstream 500, then clears fail flag. Useful with shareErrors toggles.
 *
 * <pre>
 * ./gradlew :singleflight:scripts:gatling:gatlingRun \
 *   --simulation=com.andver.singleflight.gatling.ErrorPolicySimulation --non-interactive
 * </pre>
 */
public class ErrorPolicySimulation extends Simulation {

  {
    ScenarioBuilder enableFail = scenario("enable-fail")
      .exec(
        http("fail-on")
          .post(LoadConfig.upstreamUrl() + "/admin/fail?enabled=true")
          .check(status().is(200))
      );

    ScenarioBuilder burst = scenario("error-burst")
      .exec(
        http("product-while-failing")
          .get(LoadConfig.distributedUrl() + "/products/" + LoadConfig.productId())
          .check(status().in(200, 500))
      );

    ScenarioBuilder disableFail = scenario("disable-fail")
      .exec(
        http("fail-off")
          .post(LoadConfig.upstreamUrl() + "/admin/fail?enabled=false")
          .check(status().is(200))
      );

    setUp(
      enableFail.injectOpen(atOnceUsers(1)),
      burst.injectOpen(nothingFor(Duration.ofSeconds(1)), rampUsers(30).during(Duration.ofSeconds(2))),
      disableFail.injectOpen(nothingFor(Duration.ofSeconds(5)), atOnceUsers(1))
    )
      .protocols(http.baseUrl(LoadConfig.distributedUrl()).shareConnections())
      .maxDuration(Duration.ofSeconds(20));
  }
}
