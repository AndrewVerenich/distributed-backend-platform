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
  "cdc-application-events-engine:simple-notification-microservice",

  "transactional-outbox-engine:outbox-model",
  "transactional-outbox-engine:outbox-publisher-starter",
  "transactional-outbox-engine:outbox-consumer-starter",
  "transactional-outbox-engine:simple-order-microservice",
  "transactional-outbox-engine:simple-notification-microservice",

  "websocket-gateway:websocket-gateway",
  "websocket-gateway:client-notification-model",
  "websocket-gateway:client-notification",
  "websocket-gateway:client-notification-sender-starter",
  "websocket-gateway:client-notification-consumer-starter",
  "websocket-gateway:simple-payment-microservice",

  "unique-id-generator:unique-id-generator",
  "unique-id-generator:simple-client",

  "client-request-deduplicator:client-request-deduplicator-starter",
  "client-request-deduplicator:simple-client-microservice",
  "client-request-deduplicator:simple-server-microservice",

  "time-service:time-service",
  "time-service:time-client-starter",
  "time-service:simple-client-microservice",

  "event-sourcing-cqrs-banking:banking-domain",
  "event-sourcing-cqrs-banking:banking-command-api",
  "event-sourcing-cqrs-banking:projection-balance",
)
