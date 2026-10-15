# Metrics Coverage Plan — Senior Portfolio Observability

Цель: закрыть проекты без доменных метрик так, чтобы каждый **доказывал свой claim цифрами** (Micrometer → Prometheus → Grafana + story на дашборде), а не только JVM-дефолтами.

Эталон в репо: `fifo-waiting-room`, `distributed-hash-map`, `cache-eviction`, `server-push-gateways`, `saga-orchestrator`.

---

## Definition of Done (senior bar)

1. Domain SLI — метрики доказывают тезис README.
2. Стартерный контракт где есть starter (`XxxMetrics` + Micrometer), иначе класс метрик в сервисе.
3. Compose: Actuator `/actuator/prometheus` + scrape + Grafana dashboard (6–12 панелей).
4. Story на дашборде: happy path vs failure / trade-off.
5. README: таблица метрик + «что смотреть при демо».
6. (Желательно) сценарий нагрузки, без которого панели пустые.

**Не делать:** одинаковый шаблон JVM CPU/heap на все проекты.

---

## Текущее состояние (аудит)

| Уровень | Проекты |
|--------|---------|
| **Gold** | `saga-orchestrator`, `distributed-hash-map`, `cache-eviction`, `server-push-gateways`, `fifo-waiting-room` |
| **Partial** | `client-request-deduplicator`, `auth-gateway`, `websocket-gateway` |
| **None** | остальные 12 |

---

## Sprint A — высокий ROI (in progress)

| # | Проект | Claim → ключевые метрики | Дашборд-story | Status |
|---|--------|--------------------------|---------------|--------|
| 1 | **high-load-counter** | hot-key / shard / HLL | `hlc_views_*`, `hlc_redis_incr`, `hlc_hll_*`, `hlc_flush_*`, shard skew | ровный шардинг vs hot key | ✅ |
| 2 | **transactional-outbox-engine** | exactly-once / lag | `outbox_published`, `outbox_processed{result}`, process latency, duplicates skipped | publish → consume + skip dup | ✅ |
| 3 | **event-sourcing-cqrs-banking** | projection lag / replay | `es_events_appended`, `es_projection_*`, `es_snapshots`, temporal query latency | write vs projection lag | ✅ |

---

## Sprint B — Partial + routing ✅

| # | Проект | Что добавить | Status |
|---|--------|--------------|--------|
| 4 | `client-request-deduplicator` | Prometheus + Grafana на уже существующие hit/miss/bypass | ✅ |
| 5 | `auth-gateway` | Формализовать login/refresh/revoke/fingerprint + Grafana | ✅ |
| 6 | `websocket-gateway` | messages, fanout latency, reconnects + стек | ✅ |
| 7 | `consistent-hash-router` | route_total{node}, rebalance_keys_moved | ✅ |
| 8 | `db-sharding` | ops_by_shard, scatter_gather_latency, shard_skew | ✅ |

Порты: dedup 9099/3007 · auth 9100/3008 · ws 9101/3009 · chr 9102/3010 · shard 9103/3011

---

## Sprint C — scheduler / gateway

| # | Проект | Фокус |
|---|--------|-------|
| 9 | `distributed-task-scheduler` | lock_acquired/denied, duplicate_prevented, job_duration |
| 10 | `bff-gateway` | rate_limited, cb_state, aggregation_latency{bff} |

---

## Sprint D — хвост

| # | Проект | Фокус |
|---|--------|-------|
| 11 | `unique-id-generator` | ids/sec, clock-backward waits |
| 12 | `time-service` | skew_ms, resync, offset cache hit |
| 13 | `dynamic-application-config` | config push lag, apply success/fail |
| 14 | `cdc-application-events-engine` | CDC lag, Camel throughput/errors |
| 15 | `realtime-trends` | stream latency, recommendation p99 |

---

## Шаблон реализации

```
project/
  *-starter/.../metrics/XxxMetrics.kt          # domain API (если starter)
  *-starter/.../metrics/MicrometerXxxMetrics.kt
  demo-*/src/main/resources/application.yml    # management.endpoints + prometheus
  prometheus/prometheus.yml
  grafana/provisioning/{datasources,dashboards}/
  grafana/dashboards/<project>.json
  docker-compose.yml                           # + prometheus + grafana
  README.md                                    # Metrics table + Demo walkthrough
```

Deps: `micrometer-core` (api) в starter; `actuator` + `micrometer-registry-prometheus` в demo.

Уникальные host-порты (избегать коллизий с уже занятыми):

| Project | Prometheus | Grafana |
|---------|------------|---------|
| distributed-hash-map | 9090 | 3001 |
| saga-orchestrator | (см. compose) | — |
| cache-eviction | (см. compose) | — |
| server-push-gateways | (см. compose) | — |
| fifo-waiting-room | 9095 | 3003 |
| **high-load-counter** | **9096** | **3004** |
| **transactional-outbox-engine** | **9097** | **3005** |
| **event-sourcing-cqrs-banking** | **9098** | **3006** |

---

## Новые проекты (`next-steps`)

`load-shedding`, `distributed-lock-fencing`, `rate-limiter-algorithms`, `circuit-breaker-from-scratch`, `on-demand-dispatch` — метрики с первого дня по тому же DoD.
