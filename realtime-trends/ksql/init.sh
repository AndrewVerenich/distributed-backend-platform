#!/bin/sh
set -e

echo "Waiting for ksqlDB server..."
sleep 10

echo "Creating PRODUCT_VIEW_COUNTS..."
curl -X POST http://ksqldb-server:9090/ksql \
  -H "Content-Type: application/vnd.ksql.v1+json; charset=utf-8" \
  --data-binary @- <<'EOF'
{
  "ksql": "CREATE TABLE PRODUCT_VIEW_COUNTS (productId BIGINT PRIMARY KEY, categoryId BIGINT, views BIGINT, ts BIGINT) WITH (KAFKA_TOPIC='product-view-counts', VALUE_FORMAT='JSON');"
}
EOF

echo "Creating QUERYABLE_PRODUCT_VIEW_COUNTS..."
curl -X POST http://ksqldb-server:9090/ksql \
  -H "Content-Type: application/vnd.ksql.v1+json; charset=utf-8" \
  --data-binary @- <<'EOF'
{
  "ksql": "CREATE TABLE QUERYABLE_PRODUCT_VIEW_COUNTS AS SELECT productId, categoryId, views, ts FROM PRODUCT_VIEW_COUNTS EMIT CHANGES;"
}
EOF

echo "Init complete!"
