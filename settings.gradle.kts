rootProject.name = "distributed-backend-platform"

include(
  "dynamic-application-config",
  "dynamic-application-config:dynamic-config-starter",
  "dynamic-application-config:dynamic-config-engine",
  "dynamic-application-config:simple-microservice",

  "realtime-trends:product-view-generator",
  "realtime-trends:trends-analyzer",
  "realtime-trends:product-service",

  "distributed-task-scheduler:task-runner",
  "distributed-task-scheduler:task-starter",
  "distributed-task-scheduler:simple-user-microservice",
  "distributed-task-scheduler:simple-order-microservice",

  "cdc-application-events-engine:application-events-handler",
)
