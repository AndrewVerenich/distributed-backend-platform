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

### 2. [realtime-trends](./realtime-trends/README.md)
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

### 3. [distributed-task-scheduler](./distributed-task-scheduler/README.md)
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
