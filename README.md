# Distributed Backend Platform

Коллекция реализаций паттернов и решений для распределённых систем на базе Spring Boot и Kotlin/Java. Проект 
демонстрирует практический опыт работы с микросервисной архитектурой, реактивными системами, event-driven подходами, 
CDC (Change Data 
Capture), распределёнными транзакциями и другими ключевыми концепциями современных распределённых систем.

Каждый проект представляет собой полноценное решение с использованием современных технологий и best practices, готовое к использованию в production-окружении.

## 📂 Проекты

### 1. [dynamic-application-config](./dynamic-application-config/README.md)
Реализация системы динамического управления конфигурациями в микросервисной архитектуре.  

**Описание:**
- Хранение конфигов в PostgreSQL.
- CDC через Debezium → Kafka.
- Dynamic Config Engine на Kafka Streams для трансляции и анализа консистентности.
- Spring Boot Starter для интеграции в микросервисы.
- Fault tolerance и Observability.

**Стек:**
- Kotlin / Java 21 
- Spring Boot 3
- Spring Kafka / Kafka Streams
- Debezium
- PostgreSQL
- Docker Compose

---

### 2. [cdc-application-events-engine](./cdc-application-events-engine/README.md)
Реализация event-driven архитектуры на основе Change Data Capture (CDC) для обеспечения согласованности данных между микросервисами без использования распределённых транзакций.

**Описание:**
- CDC через Debezium для отслеживания изменений в MySQL.
- Трансформация и маршрутизация событий через Apache Camel YAML DSL.
- Декларативные маршруты для обработки бизнес-событий (user, credit, payment).
- Eventual consistency вместо распределённых транзакций (2PC/Saga).

**Стек:**
- Kotlin / Java 21
- Spring Boot 3
- Apache Camel 4.4 (YAML DSL)
- Debezium 2.5
- Apache Kafka
- MySQL 8.0
- Docker Compose

---

### 3. [realtime-trends](./realtime-trends/README.md)
Реализация рекомендательной системы с персонализацией и анализом трендов в реальном времени.

**Описание:**
- Сбор событий просмотров продуктов через Kafka.
- Подсчёт трендов и популярности товаров с помощью Kafka Streams.
- Потоковая база данных ksqlDB с SQL‑доступом к агрегированным данным.
- Хранение пользовательских предпочтений в PostgreSQL.
- REST‑сервис на Spring WebFlux для персонализированных рекомендаций.

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux, R2DBC)
- Apache Kafka / Kafka Streams
- ksqlDB
- PostgreSQL
- Docker Compose

---

### 4. [distributed-task-scheduler](./distributed-task-scheduler/README.md)
Реализация распределённой системы планирования задач по расписанию для микросервисной архитектуры.

**Описание:**
- Централизованный оркестратор задач (task-runner) с cron-планированием.
- Spring Boot Starter для автоматической интеграции в микросервисы.
- Распределённые блокировки через Redis (Redisson) для предотвращения дублирования.
- Асинхронная передача статусов выполнения через Kafka.
- Хранение истории выполнения задач в PostgreSQL.

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux, R2DBC)
- Spring Kafka
- Redisson (Redis)
- PostgreSQL
- Docker Compose

---

### 5. [transactional-outbox-engine](./transactional-outbox-engine/README.md)
Реализация Transactional Outbox Pattern с использованием Debezium CDC для гарантированной доставки событий между микросервисами.

**Описание:**
- Атомарная публикация событий в outbox таблицу в той же транзакции, что и основная бизнес-операция.
- Автоматическая публикация в Kafka через Debezium с использованием EventRouter и ExtractNewRecordState SMT.
- Exactly-once семантика через Debezium `exactly.once.support=REQUIRED` и идемпотентную обработку на стороне consumer.
- Spring Boot Starter для публикации (outbox-publisher-starter) и обработки (outbox-consumer-starter) событий.
- Идемпотентная обработка через проверку `idempotency_key` с блокировкой `SELECT FOR UPDATE`.

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux, R2DBC)
- Spring Kafka
- Debezium 2.5 (PostgreSQL Connector)
- Kafka Connect SMT (EventRouter, ExtractNewRecordState)
- PostgreSQL
- Docker Compose

---

### 6. [websocket-gateway](./websocket-gateway/README.md)
Реализация WebSocket Gateway для двусторонней коммуникации в реальном времени между сервером и клиентами в микросервисной архитектуре.

**Описание:**
- WebSocket Gateway с JWT-авторизацией для управления соединениями клиентов.
- Redis Pub/Sub для доставки real-time уведомлений пользователям через каналы подписок.
- Интеграция с Kafka для получения событий от микросервисов и отправки событий от клиентов.
- Spring Boot Starters для простой интеграции отправки и обработки событий в микросервисах.
- Реактивная архитектура на Spring WebFlux для масштабируемости и эффективности.

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux)
- Spring Data Redis (Reactive)
- Nimbus JOSE + JWT
- Apache Kafka
- Redis Pub/Sub
- Docker Compose

---

### 7. [unique-id-generator](./unique-id-generator/README.md)
Реализация распределённого генератора уникальных идентификаторов на основе алгоритма Snowflake для микросервисной архитектуры.

**Описание:**
- Генерация уникальных 64-битных ID с использованием алгоритма Snowflake.
- Автоматическое определение worker ID через Eureka Service Discovery.
- Горизонтальное масштабирование через Nginx Load Balancer.
- Высокая производительность — до 4096 ID/мс на инстанс без обращения к БД.
- Глобальная уникальность без координации между инстансами.

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux)
- Spring Cloud Netflix Eureka Client
- Nginx (Load Balancer)
- Docker Compose

---

### 8. [client-request-deduplicator](./client-request-deduplicator/README.md)
Реализация клиентской дедупликации HTTP-запросов для предотвращения дублирования запросов и снижения нагрузки на микросервисы.

**Описание:**
- Генерация fingerprint запросов на основе HTTP метода, URI и тела запроса.
- Кэширование успешных ответов (2xx) в Redis с настраиваемым TTL.
- Исключение временных полей и query параметров из fingerprint для корректной дедупликации.
- Spring Boot Starter для автоматической интеграции через WebClient Filter.
- Метрики для мониторинга эффективности кэширования (hit/miss/bypass).

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux)
- Spring Data Redis (Reactive)
- Jackson
- Micrometer
- Docker Compose

---

### 9. [time-service](./time-service/README.md)
Реализация сервиса компенсации рассинхронизации времени (Clock Skew Compensation) для распределенных систем.

**Описание:**
- Time Service как единый источник истины о времени.
- Автоматическая компенсация clock skew через измерение offset относительно Time Service.
- Spring Boot Starter для интеграции в микросервисы.
- Периодическая ресинхронизация для компенсации clock drift.
- Кэширование offset в Redis для отказоустойчивости.

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux)
- Spring Data Redis (Reactive)
- Docker Compose

---

### 10. [event-sourcing-cqrs-banking](./event-sourcing-cqrs-banking/README.md)
Реализация Event Sourcing и CQRS паттернов для банковской системы с поддержкой temporal queries и snapshots.

**Описание:**
- Event Store в PostgreSQL для хранения всей истории событий с версионированием.
- CQRS разделение на Command API (write) и Query API (read) с независимым масштабированием.
- Debezium CDC для автоматической публикации событий из Event Store в Kafka.
- Projection Service для асинхронного обновления Read Model из событий.
- Temporal queries — получение баланса счёта на любой момент времени через event replay.
- Snapshots для оптимизации восстановления состояния при большом количестве событий.

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux, R2DBC)
- Spring Kafka
- Debezium 2.5 (PostgreSQL Connector)
- PostgreSQL
- Docker Compose

---

### 11. [auth-gateway](./auth-gateway/README.md)
Реализация JWT-based Authentication Gateway с access/refresh токенами, refresh token rotation и централизованной валидацией.

**Описание:**
- Auth Service для управления жизненным циклом токенов (login, refresh, logout).
- Gateway Service как единая точка входа с JWT валидацией в Spring Security.
- Access токены + Refresh токены с rotation при каждом обновлении.
- Fingerprint binding для защиты от кражи refresh токенов (User-Agent + IP).
- Token family tracking для обнаружения компрометации и автоматического отзыва.
- Nginx как reverse proxy с rate limiting на критичные эндпоинты (login, refresh).
- Хранение refresh токенов в PostgreSQL с отслеживанием статуса (ACTIVE/USED/REVOKED).

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux, R2DBC)
- Spring Security
- JJWT (JWT generation/parsing)
- PostgreSQL
- Redis (optional blacklist)
- Nginx (rate limiting)
- Docker Compose

---

### 12. [high-load-counter](./high-load-counter/README.md)
Реализация высоконагруженной системы подсчёта просмотров видео с шардированными счётчиками в Redis и windowed aggregation через Kafka Streams.

**Описание:**
- Буферизация событий просмотра через Kafka — поглощение пиковых нагрузок без потери данных.
- Windowed aggregation (Kafka Streams) — tumbling windows с grace period для сокращения записей в Redis.
- Sharded counter в Redis — 8 шардов на видео для устранения hot key при высоком throughput.
- HyperLogLog для подсчёта уникальных зрителей — ~12 KB на видео вместо сотен МБ для SET.
- Периодический flush в PostgreSQL для долговременного хранения.
- REST API: `GET /counters/{videoId}`, `POST /counters/{videoId}/view`.

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux, R2DBC)
- Spring Kafka / Kafka Streams
- Spring Data Redis (Reactive)
- Apache Kafka
- Redis 7.2
- PostgreSQL
- Docker Compose

---

### 13. [consistent-hash-router](./consistent-hash-router/README.md)
Реализация компонента маршрутизации HTTP-запросов по алгоритму Consistent Hashing для стабильного key-based routing в распределённых системах.

**Описание:**
- Маршрутизация запросов на backend-ноды по `routingKey` через consistent hash ring.
- Виртуальные ноды для более равномерного распределения ключей.
- Минимальное перераспределение ключей при добавлении/удалении ноды.
- Service discovery через Eureka: backend-инстансы регистрируются динамически, а роутер синхронизирует состав ring из реестра.
- Admin API для диагностики ring и проверки маршрутизации конкретного ключа.

**Стек:**
- Kotlin / Java 21
- Spring Boot 3 (WebFlux, Scheduling)
- Spring Cloud Netflix Eureka Client
- Reactor WebClient
- MockWebServer (integration tests)
- Docker Compose
