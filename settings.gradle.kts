rootProject.name = "distributed-backend-platform"

include(
  "dynamic-application-config",
  "dynamic-application-config:dynamic-config-starter",
  "dynamic-application-config:dynamic-config-engine",
)

include("dynamic-application-config:simple-microservice")