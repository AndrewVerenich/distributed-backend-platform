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

  "auth-gateway:gateway-service",
  "auth-gateway:auth-service",
  "auth-gateway:resource-service",

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
  "event-sourcing-cqrs-banking:banking-query-api",
  "event-sourcing-cqrs-banking:projection-balance",

  "high-load-counter:view-event-producer",
  "high-load-counter:counter-aggregator",
  "high-load-counter:counter-service",

  "consistent-hash-router:hash-router-service",
  "consistent-hash-router:simple-backend-service",

  "db-sharding:sharding-starter",
  "db-sharding:simple-user-service",

  "bff-gateway:api-gateway",
  "bff-gateway:bff-web",
  "bff-gateway:bff-mobile",
  "bff-gateway:bff-admin",
  "bff-gateway:user-service",
  "bff-gateway:product-service",

  "saga-orchestrator:saga-model",
  "saga-orchestrator:saga-orchestrator-engine",
  "saga-orchestrator:saga-participant-starter",
  "saga-orchestrator:travel-flight-service",
  "saga-orchestrator:travel-hotel-service",
  "saga-orchestrator:travel-car-service",

  "distributed-hash-map:map-starter",
  "distributed-hash-map:demo-client-service-a",
  "distributed-hash-map:demo-client-service-b",

  "cache-eviction:eviction-starter",
  "cache-eviction:demo-catalog-service",

  "server-push-gateways:push-event-model",
  "server-push-gateways:event-bridge",
  "server-push-gateways:push-gateway",
  "server-push-gateways:push-sender-starter",
  "server-push-gateways:event-producer-demo",
  "server-push-gateways:scripts:gatling",
)
