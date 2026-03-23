# consistent-hash-router

## 📌 Проблема

В распределённых системах часто нужно маршрутизировать запросы по ключу (например, `userId`) к нужному backend-инстансу.

Классический подход `hash(key) % N` плохо масштабируется при изменении числа инстансов: при добавлении/удалении ноды почти все ключи “переезжают” на другие узлы.

Например, для **stateful-сервисов**, где состояние хранится локально в памяти конкретного инстанса:
- сервисы пользовательских сессий/корзин;
- игровые комнаты или чаты с in-memory состоянием;
- шардированный кэш, где ключ должен стабильно попадать на один и тот же узел.

Если запросы одного `userId` начинают ходить по разным инстансам, нужны постоянные синхронизации или внешнее общее хранилище. Consistent hashing снижает такие “скачки” маршрута при масштабировании и упрощает работу stateful-компонентов.


## Решение

Компонент роутинга на базе **Consistent Hashing**:

- ключ запроса (`routingKey`) хешируется
- выбирается ближайшая виртуальная нода на кольце
- запрос проксируется в выбранный backend
- при изменении состава нод перераспределяется только часть ключей

```mermaid
flowchart LR
    Client -->|"POST /route/{routingKey}"| Router[hash-router-service]
    Router -->|hash of routingKey| Ring["ConsistentHashRing + virtualNodes"]
    Ring -->|lookup| Backend1[backend-1]
    Ring -->|lookup| Backend2[backend-2]
    Ring -->|lookup| Backend3[backend-3]
    Health["NodeHealthChecker"] -->|"/health"| Backend1
    Health -->|"/health"| Backend2
    Health -->|"/health"| Backend3
```

```mermaid
flowchart LR
    Key["routingKey=42"] --> Hash["MurmurHash3"]
    Hash --> Position["position on ring"]
    Position --> Ceiling["ceilingEntry(position)"]
    Ceiling --> Node["target backend node"]
```

## Архитектура

- `hash-router-service` — основной роутер, consistent hash ring, health-check, admin API
- `simple-backend-service` — простой backend для демонстрации маршрутизации
- `docker-compose.yml` — поднимает роутер и 3 backend-инстанса

Ключевые компоненты:

- `ConsistentHashRing` — `ConcurrentSkipListMap`, виртуальные ноды, поиск через `ceilingEntry`
- `MurmurHash3Function` — быстрая детерминированная хеш-функция
- `NodeRegistry` — статус нод, failure counters, add/remove из ring
- `NodeHealthChecker` — периодический health-check с авто-исключением нерабочих нод
- `RequestRouter` — proxy-запрос на выбранную ноду + заголовок `X-Routed-To`

## API

- `POST /route/{routingKey}` — маршрутизация запроса в backend
- `GET /admin/nodes` — статусы нод и число последовательных ошибок
- `GET /admin/ring/stats` — число виртуальных нод на каждый backend
- `GET /admin/ring/lookup?key=user-42` — debug lookup по ключу

## Тесты

- `ConsistentHashRingTest`:
  - равномерность распределения
  - ограниченное перераспределение при add/remove нод
  - стабильность при единственной ноде
- `RequestRouterIntegrationTest` (`@Tag("integration")`):
  - один и тот же ключ стабильно роутится в одну и ту же ноду

## Демо

1) Собрать jar:

```bash
./gradlew :consistent-hash-router:hash-router-service:bootJar
./gradlew :consistent-hash-router:simple-backend-service:bootJar
```

2) Поднять окружение:

```bash
docker-compose -f consistent-hash-router/docker-compose.yml up --build
```

3) Проверить sticky-routing:

```bash
curl -i -X POST http://localhost:8080/route/42 -d '{"event":"a"}'
curl -i -X POST http://localhost:8080/route/42 -d '{"event":"b"}'
```

В ответе будет заголовок `X-Routed-To` — для одинакового `routingKey` он должен быть одинаковым.

4) Диагностика ring:

```bash
curl http://localhost:8080/admin/nodes
curl http://localhost:8080/admin/ring/stats
curl http://localhost:8080/admin/ring/lookup?key=42
```

## Стек

- Kotlin / Java 21
- Spring Boot 3 (WebFlux, Scheduling, Configuration Properties)
- Reactor WebClient
- MockWebServer (integration tests)
- Docker Compose
