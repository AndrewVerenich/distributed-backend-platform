#!/bin/bash
set -e

register_connector() {
  local name=$1
  local dbname=$2
  local slot=$3
  local publication=$4

  echo "Registering connector $name for database $dbname..."
  curl -sf -X POST http://debezium:8083/connectors \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"$name\",
      \"config\": {
        \"connector.class\": \"io.debezium.connector.postgresql.PostgresConnector\",
        \"database.hostname\": \"postgres\",
        \"database.port\": \"5432\",
        \"database.user\": \"demo\",
        \"database.password\": \"demo\",
        \"database.dbname\": \"$dbname\",
        \"database.server.name\": \"$dbname\",
        \"table.include.list\": \"public.outbox\",
        \"plugin.name\": \"pgoutput\",
        \"slot.name\": \"$slot\",
        \"publication.name\": \"$publication\",
        \"topic.prefix\": \"debezium.$dbname\",
        \"key.converter\": \"org.apache.kafka.connect.storage.StringConverter\",
        \"key.converter.schemas.enable\": \"false\",
        \"value.converter\": \"org.apache.kafka.connect.storage.StringConverter\",
        \"value.converter.schemas.enable\": \"false\",
        \"transforms\": \"outbox,unwrap\",
        \"transforms.outbox.type\": \"io.debezium.transforms.outbox.EventRouter\",
        \"transforms.outbox.route.by.field\": \"type\",
        \"transforms.outbox.route.topic.replacement\": \"domain.\${routedByValue}\",
        \"transforms.outbox.table.field.event.key\": \"partitioning_key\",
        \"transforms.outbox.table.field.event.payload\": \"payload\",
        \"transforms.outbox.table.field.event.id\": \"id\",
        \"transforms.outbox.table.field.event.type\": \"type\",
        \"transforms.outbox.table.fields.additional.placement\": \"idempotency_key:header:idempotencyKey\",
        \"transforms.unwrap.type\": \"io.debezium.transforms.ExtractNewRecordState\",
        \"transforms.unwrap.drop.tombstones\": \"false\",
        \"transforms.unwrap.delete.handling.mode\": \"none\",
        \"decimal.handling.mode\": \"double\",
        \"database.history.kafka.bootstrap.servers\": \"kafka:9092\",
        \"database.history.kafka.topic\": \"schema-changes.$dbname\",
        \"exactly.once.support\": \"REQUIRED\"
      }
    }"
  echo
}

register_connector "booking-outbox-connector" "booking" "booking_outbox_slot" "booking_outbox_pub"
register_connector "flight-outbox-connector" "flight" "flight_outbox_slot" "flight_outbox_pub"
register_connector "hotel-outbox-connector" "hotel" "hotel_outbox_slot" "hotel_outbox_pub"
register_connector "car-outbox-connector" "car" "car_outbox_slot" "car_outbox_pub"

echo "All Debezium connectors registered."
