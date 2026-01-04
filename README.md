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
