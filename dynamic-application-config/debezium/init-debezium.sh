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
    "table.include.list": "public.business_configs",
    "plugin.name": "pgoutput",
    "slot.name": "configs_slot",
    "topic.prefix": "configs",
    "decimal.handling.mode": "double",
    "publication.name": "configs_pub",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "schema-changes.master"
  }
}'
