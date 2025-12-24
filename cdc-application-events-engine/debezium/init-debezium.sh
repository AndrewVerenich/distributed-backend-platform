#!/bin/bash
curl -X POST http://debezium:8083/connectors -H "Content-Type: application/json" -d '{
  "name": "master-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "debezium",
    "database.password": "debezium",
    "database.server.id": "184054",
    "database.server.name": "mysql_server",
    "database.include.list": "master",
    "table.include.list": "master.user,master.credit,master.payment",
    "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
    "schema.history.internal.kafka.topic": "schema-changes.master",
    "include.schema.changes": "true",
    "topic.prefix": "debezium",
    "snapshot.mode": "initial",
    "decimal.handling.mode": "double",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter.schemas.enable": "false"
  }
}'
