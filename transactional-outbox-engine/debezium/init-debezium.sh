#!/bin/bash
curl -X POST http://debezium:8083/connectors -H "Content-Type: application/json" -d '{
"name": "postgres-configs-connector",
"config": {
  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
  "database.hostname": "postgres",
  "database.port": "5432",
  "database.user": "demo",
  "database.password": "demo",
  "database.dbname": "master",
  "database.server.name": "postgres",
  "table.include.list": "public.outbox",
  "plugin.name": "pgoutput",
  "slot.name": "outbox_slot",
  "publication.name": "outbox_pub",
  "topic.prefix": "debezium",
  "key.converter": "org.apache.kafka.connect.storage.StringConverter",
  "key.converter.schemas.enable": "false",
  "value.converter": "org.apache.kafka.connect.storage.StringConverter",
  "value.converter.schemas.enable": "false",

  "transforms": "outbox,unwrap",
  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
  "transforms.outbox.route.by.field": "type",
  "transforms.outbox.route.topic.replacement": "domain.${routedByValue}",
  "transforms.outbox.table.field.event.key": "partitioning_key",
  "transforms.outbox.table.field.event.payload": "payload",
  "transforms.outbox.table.field.event.id": "id",
  "transforms.outbox.table.field.event.type": "type",
  "transforms.outbox.table.fields.additional.placement": "idempotency_key:header:idempotencyKey",

  "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
  "transforms.unwrap.drop.tombstones": "false",
  "transforms.unwrap.delete.handling.mode": "none",

  "decimal.handling.mode": "double",
  "database.history.kafka.bootstrap.servers": "kafka:9092",
  "database.history.kafka.topic": "schema-changes.master",
  "exactly.once.support": "REQUIRED"
  }
}
'
