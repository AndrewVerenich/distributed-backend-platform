#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
  CREATE DATABASE booking;
  CREATE DATABASE flight;
  CREATE DATABASE hotel;
  CREATE DATABASE car;
  CREATE DATABASE tracker;
EOSQL

for db in booking flight hotel car tracker; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -d "$db" -f "/schemas/common.sql"
done

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -d booking -f "/schemas/booking.sql"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -d flight -f "/schemas/flight.sql"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -d hotel -f "/schemas/hotel.sql"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -d car -f "/schemas/car.sql"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -d tracker -f "/schemas/tracker.sql"
