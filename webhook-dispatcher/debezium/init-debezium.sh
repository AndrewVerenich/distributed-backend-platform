#!/bin/bash
set -e

echo "Waiting for Debezium Connect..."
until curl -sf http://debezium:8083/connectors >/dev/null; do
  sleep 2
done

curl -X PUT http://debezium:8083/connectors/webhook-events-connector/config \
  -H "Content-Type: application/json" \
  -d '{
  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
  "database.hostname": "postgres",
  "database.port": "5432",
  "database.user": "webhook",
  "database.password": "webhook",
  "database.dbname": "webhook",
  "database.server.name": "postgres",
  "table.include.list": "public.events",
  "plugin.name": "pgoutput",
  "slot.name": "webhook_events_slot",
  "publication.name": "webhook_events_pub",
  "topic.prefix": "debezium",
  "key.converter": "org.apache.kafka.connect.storage.StringConverter",
  "key.converter.schemas.enable": "false",
  "value.converter": "org.apache.kafka.connect.storage.StringConverter",
  "value.converter.schemas.enable": "false",

  "transforms": "outbox,unwrap",
  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
  "transforms.outbox.route.by.field": "type",
  "transforms.outbox.route.topic.replacement": "webhook.${routedByValue}",
  "transforms.outbox.table.field.event.key": "partitioning_key",
  "transforms.outbox.table.field.event.payload": "payload",
  "transforms.outbox.table.field.event.id": "id",
  "transforms.outbox.table.field.event.type": "type",

  "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
  "transforms.unwrap.drop.tombstones": "false",
  "transforms.unwrap.delete.handling.mode": "none",

  "decimal.handling.mode": "double",
  "database.history.kafka.bootstrap.servers": "kafka:9092",
  "database.history.kafka.topic": "schema-changes.webhook",
  "exactly.once.support": "REQUIRED"
}'

echo "Connector registered."
