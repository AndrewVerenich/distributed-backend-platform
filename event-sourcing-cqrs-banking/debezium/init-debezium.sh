#!/bin/bash
curl -X POST http://debezium:8083/connectors -H "Content-Type: application/json" -d '{
"name": "postgres-cqrs-connector",
"config": {
  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
  "database.hostname": "postgres",
  "database.port": "5432",
  "database.user": "admin",
  "database.password": "admin",
  "database.dbname": "master",
  "database.server.name": "postgres",
  "table.include.list": "public.event_store",
  "plugin.name": "pgoutput",
  "slot.name": "cqrs_slot",
  "publication.name": "cqrs_pub",
  "topic.prefix": "debezium",
  "key.converter": "org.apache.kafka.connect.json.JsonConverter",
  "key.converter.schemas.enable": "false",
  "value.converter": "org.apache.kafka.connect.json.JsonConverter",
  "value.converter.schemas.enable": "false",

  "transforms": "unwrap",
  "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
  "transforms.unwrap.drop.tombstones": "true",
  "transforms.unwrap.delete.handling.mode": "none",
  "transforms.unwrap.key.field": "aggregate_id",

  "decimal.handling.mode": "double",
  "database.history.kafka.bootstrap.servers": "kafka:9092",
  "database.history.kafka.topic": "schema-changes.master",
  "exactly.once.support": "REQUIRED"
  }
}
'
