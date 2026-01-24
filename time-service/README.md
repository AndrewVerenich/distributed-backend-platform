# time-service

## Clock Skew Compensation Service

Сервис для компенсации рассинхронизации времени (clock skew) в распределенных системах.

## 📌 Проблема

В распределенных системах разные узлы имеют рассинхронизированные часы, что приводит к:
- **Нарушению порядка событий** — события могут обрабатываться в неправильном порядке
- **Неправильным TTL и истечению сроков** — данные могут удаляться преждевременно или оставаться дольше, чем нужно
- **Проблемам с распределенными блокировками** — race conditions из-за неправильного определения истечения блокировок
- **Проблемам с логированием и отладкой** — события из разных узлов выглядят не в хронологическом порядке

### Что такое Clock Skew?

**Clock Skew** (расхождение часов) — это разница во времени между часами разных узлов в распределенной системе.

Причины:
- Физические особенности кварцевых генераторов (каждый работает с небольшой погрешностью)
- Температура и нагрузка процессора влияют на скорость работы генератора
- Отсутствие синхронизации с единым источником времени

---

## 🎯 Решение

**Time Service** предоставляет единый источник истины о времени, а клиенты автоматически компенсируют clock skew.

### Архитектура

```
┌─────────────────────────────────────────────────────────┐
│              Time Service (Source of Truth)             │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Clock Synchronization Layer                      │  │
│  │  - Синхронизация с NTP серверами                  │  │
│  │  - Мониторинг точности часов                      │  │
│  │  - Вычисление uncertainty (ε)                     │  │
│  └───────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Time API                                         │  │
│  │  GET /time/now → {time, uncertainty}              │  │
│  │  POST /time/sync/start → serverTime               │  │
│  │  POST /time/sync/complete → {offset, RTT}         │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                        │
                        │ HTTP/gRPC
                        │
        ┌───────────────┼───────────────┐
        │               │               │
┌───────▼──────┐ ┌──────▼──────┐ ┌─────▼──────┐
│   Client 1   │ │   Client 2   │ │  Client 3  │
│              │ │              │ │            │
│ ┌──────────┐ │ │ ┌──────────┐ │ │ ┌────────┐ │
│ │TimeClient│ │ │ │TimeClient│ │ │ │TimeClient│
│ │Starter   │ │ │ │Starter   │ │ │ │Starter │ │
│ └────┬─────┘ │ │ └────┬─────┘ │ │ └───┬────┘ │
│      │       │ │      │       │ │     │      │
│ ┌────▼─────┐ │ │ ┌────▼─────┐ │ │ ┌───▼────┐ │
│ │Offset    │ │ │ │Offset    │ │ │ │Offset  │ │
│ │Cache     │ │ │ │Cache     │ │ │ │Cache   │ │
│ │(Redis)   │ │ │ │(Redis)   │ │ │ │(Redis) │ │
│ └──────────┘ │ │ └──────────┘ │ │ └────────┘ │
└──────────────┘ └──────────────┘ └────────────┘
```

### Как это работает?

1. **Измерение Clock Skew (Offset)**
   - Клиент выполняет синхронизацию с Time Service
   - Используется протокол, аналогичный NTP, для измерения round-trip time
   - Вычисляется offset: `offset = serverTime - clientTime - (RTT / 2)`

2. **Сохранение Offset**
   - Offset сохраняется в Redis (для шардирования и мониторинга)
   - Также кэшируется локально для быстрого доступа

3. **Применение Offset**
   - Когда клиенту нужно время, он применяет offset:
     ```kotlin
     логическое_время = локальное_время + offset
     ```

4. **Периодическая ресинхронизация**
   - Клиент периодически (каждые 30 секунд) обновляет offset
   - Компенсирует clock drift (постепенное расхождение часов)

---

## 🛠️ Использование

### 1. Добавление зависимостей

В `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":time-service:time-client-starter"))
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
}
```

### 2. Настройка

В `application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

time:
  service-url: http://localhost:8080
  node-id: my-service-1
  sync:
    enabled: true
    interval-ms: 30000  # Интервал синхронизации (30 секунд)
```

### 3. Использование в коде

```kotlin
@Autowired
private lateinit var logicalTimeService: LogicalTimeService

// Вместо System.currentTimeMillis()
val time = logicalTimeService.getLogicalTime()

// Вместо Instant.now()
val instant = logicalTimeService.getLogicalInstant()
```

---

## 📡 API

### Time Service Endpoints

#### GET /time/now
Возвращает текущее время сервера.

**Response:**
```json
{
  "time": 1704067200000,
  "uncertainty": 10
}
```

#### POST /time/sync/start
Первая фаза синхронизации — клиент отправляет свой nodeId и локальное время.

**Request:**
```json
{
  "nodeId": "client-1",
  "localTime": 1704067200000
}
```

**Response:**
```json
1704067200100
```

#### POST /time/sync/complete
Вторая фаза синхронизации — клиент отправляет временные метки для вычисления offset.

**Request:**
```
POST /time/sync/complete?nodeId=client-1&clientT0=1704067200000&clientT3=1704067200010
```

**Response:**
```json
{
  "serverTime": 1704067200005,
  "roundTripTime": 10,
  "offset": -5
}
```
---

## 🎬 Демо

### 1. Запуск окружения

```bash
cd time-service
.././gradlew clean build
docker-compose up -d
```

### 2. Проверка работы Time Service

```bash
# Получить текущее время
curl http://localhost:8080/time/now
```

### 3. Проверка работы клиента

```bash
# Логи клиента
docker logs simple-client

#com.andver.example.TimeDemo              : Logical time: 1769283588489
#com.andver.example.TimeDemo              : Logical instant: 2026-01-24T19:39:48.489Z
#com.andver.example.TimeDemo              : Local time: 1769283588477
#com.andver.example.TimeDemo              : Clock offset: 12ms
```

### 4. Мониторинг синхронизации

В логах клиента будут видны сообщения о синхронизации:
```
c.a.t.s.scheduler.TimeSyncScheduler      : Sync completed: offset=5ms, RTT=2ms
```

---

## 🛡️ Отказоустойчивость

- **Использование кэшированного offset**  
  Если Time Service недоступен, клиент использует последний известный offset из Redis.

- **Периодическая ресинхронизация**  
  Клиент автоматически обновляет offset каждые 30 секунд, компенсируя clock drift.

- **Мониторинг дрейфа**  
  При значительном изменении offset (более 100 мс) логируется предупреждение.

---

## 📦 Структура проекта

```
time-service/
├── time-service/                      # Основной сервис времени
│   ├── src/main/kotlin/
│   │   └── com/andver/timeservice/
│   │       ├── TimeServiceApp.kt
│   │       ├── controller/
│   │       │   └── TimeController.kt
│   │       ├── service/
│   │       │   ├── TimeService.kt
│   │       │   └── ClockSynchronizationService.kt
│   │       └── model/
│   │           ├── TimeResponse.kt
│   │           ├── SyncRequest.kt
│   │           └── SyncResponse.kt
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── build.gradle.kts
│   └── Dockerfile
├── time-client-starter/                # Spring Boot Starter для клиентов
│   ├── src/main/kotlin/
│   │   └── com/andver/time/starter/
│   │       ├── TimeAutoConfiguration.kt
│   │       ├── client/
│   │       │   └── TimeServiceClient.kt
│   │       ├── service/
│   │       │   └── LogicalTimeService.kt
│   │       ├── scheduler/
│   │       │   └── TimeSyncScheduler.kt
│   │       └── cache/
│   │           └── OffsetCache.kt
│   ├── src/main/resources/
│   │   └── META-INF/spring/
│   │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── build.gradle.kts
├── simple-client-microservice/         # Пример использования
│   ├── src/main/kotlin/
│   │   └── com/andver/example/
│   │       └── SimpleClientApp.kt
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── build.gradle.kts
│   └── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## 🔧 Стек технологий

- **Kotlin / Java 21**
- **Spring Boot 3 (WebFlux)**
- **Spring Data Redis (Reactive)**
- **Docker Compose**

---
### Преимущества

- ✅ Единый источник истины — все узлы используют одно логическое время
- ✅ Автоматическая компенсация — клиент скрывает сложность
- ✅ Отказоустойчивость — работает даже при временной недоступности Time Service
- ✅ Прозрачность — минимальные изменения в коде (замена `System.currentTimeMillis()`)
- ✅ Мониторинг — отслеживание clock skew для всех узлов

