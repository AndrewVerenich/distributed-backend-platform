# websocket-gateway

## 📌 Проблема

В микросервисной архитектуре возникает необходимость **двусторонней коммуникации в реальном времени** между сервером и клиентами (веб/мобильные приложения) для:
- Доставки уведомлений пользователям
- Получения событий от клиентов (действия пользователей)
- Обеспечения real-time взаимодействия без постоянных HTTP-запросов (polling)

Основные ограничения классического подхода:
- **HTTP Polling**  
  Постоянные HTTP-запросы создают избыточную нагрузку на сервер, высокое потребление батареи на мобильных устройствах, задержки в доставке сообщений.
- **Отсутствие авторизации на уровне протокола**  
  Нет единого механизма аутентификации для WebSocket-соединений.
- **Сложность масштабирования**  
  При горизонтальном масштабировании необходимо обеспечить маршрутизацию сообщений к нужным инстансам сервиса.
- **Нет интеграции с event-driven архитектурой**  
  Сложно интегрировать WebSocket с существующими Kafka-топиками и event-driven компонентами.

---

## 🎯 Решение

**WebSocket Gateway** — реактивный шлюз для двусторонней коммуникации, который объединяет:
- **WebSocket Gateway** — сервис для управления WebSocket-соединениями с JWT-авторизацией
- **Redis Pub/Sub** — для доставки уведомлений пользователям через каналы подписок
- **Kafka** — для интеграции с event-driven архитектурой (получение и отправка событий)
- **Spring Boot Starters** — для простой интеграции в микросервисы

Таким образом:
- клиенты получают **real-time уведомления** через WebSocket,
- события от клиентов **интегрируются** в Kafka-топики,
- система **масштабируется** горизонтально через Redis Pub/Sub,
- **JWT-авторизация** обеспечивает безопасность соединений.

```mermaid
flowchart TB
    Client1[Web Client 1] -->|WebSocket + JWT| Gateway[websocket-gateway]
    Client2[Web Client 2] -->|WebSocket + JWT| Gateway
    Mobile[Mobile App] -->|WebSocket + JWT| Gateway
    
    Gateway -->|Subscribe| Redis[(Redis Pub/Sub<br/>USER_SUBSCRIPTION:userId)]
    Gateway -->|Publish Events| Kafka1[(kafka topic domain.client.event)]
    
    ClientNotification[client-notification] -->|Publish| Redis
    ClientNotification -->|Consume| Kafka2[(kafka topic domain.server.event)]
    
    Kafka1 -->|Consume| Microservice1[Microservice 1]
    Microservice2[Microservice 2] -->|Publish| Kafka2
    
```

---

# Компоненты

## ⚙️ websocket-gateway

Основной сервис для управления WebSocket-соединениями.

### 🛠️ Стек технологий
- **Kotlin / Java 21**
- **Spring Boot 3 (WebFlux)**
- **Spring Data Redis (Reactive)**
- **Nimbus JOSE + JWT**
- **Micrometer (метрики)**

### 🔧 Основные компоненты

#### 1. HandshakeHandler
- Обрабатывает установление WebSocket-соединения
- Извлекает JWT-токен из заголовка `Authorization: Bearer <token>`
- Валидирует токен через `JwtService` и извлекает `userId`
- Отклоняет неавторизованные подключения с кодом 401
- Собирает метрики количества активных соединений

#### 2. UserChannelHandler
- Обрабатывает входящие и исходящие сообщения для каждого WebSocket-соединения
- Десериализует входящие сообщения от клиентов
- Маршрутизирует события через `ClientMessageDispatcher`
- Подписывается на уведомления для пользователя через `UserSubscriptionService`

#### 3. AuthorizationService & JwtService
- Валидация JWT-токенов (подпись, срок действия)
- Настраивается через `jwt.secret-key` в конфигурации

#### 4. UserSubscriptionService
- Подписывается на Redis Pub/Sub каналы `USER_SUBSCRIPTION:{userId}`
- Использует `ReactiveRedisTemplate` для неблокирующей подписки
- Возвращает `Flux<String>` для реактивной доставки сообщений

#### 5. ClientMessageDispatcher & Processors
- Диспетчеризация сообщений от клиентов по типам (`DOMAIN`, `SYSTEM`)
- `DomainMessageProcessor` — отправка событий в Kafka топик `domain.client.event`
- `SystemMessageProcessor` — обработка системных сообщений (ping/pong)

---

## ⚙️ client-notification

Сервис для интеграции событий между WebSocket и Kafka.

### 🔧 Основные компоненты

#### 1. DomainServerEventProcessor
- Получает события из Kafka топика `domain.server.event`
- Публикует события в Redis Pub/Sub канал `USER_SUBSCRIPTION:{userId}`

#### 2. DomainClientEventProcessor
- Обрабатывает события от WebSocket-клиентов (через `DomainMessageProcessor`)
- Валидирует payload событий
- Публикует события в соответствующие Kafka-топики по типу события

---

## 📦 Стартеры для интеграции

### client-notification-sender-starter

Spring Boot Starter для отправки событий клиентам через WebSocket.

**Использование:**
```kotlin
@Autowired
private lateinit var clientNotificationSender: ClientNotificationSender

fun sendNotification(userId: Long, event: DomainServerEvent<MyPayload>) {
    clientNotificationSender.send(event)
        .subscribe()
}
```

**Автоматически создаёт:**
- `KafkaTemplate<String, DomainServerEvent<out Any>>`
- `ClientNotificationSender` bean

### client-notification-consumer-starter

Spring Boot Starter для обработки событий от клиентов.

**Использование:**
```kotlin
@Component
class MyEventHandler : ClientNotificationEventHandler {
    override val eventType = "my.event.type"
    
    override fun handle(event: DomainClientEvent): Mono<Void> {
        // Обработка события
        return Mono.empty()
    }
}
```

**Настраивается через:**
```yaml
client:
  notification:
    consumer:
      topics: my.event.type,another.event.type
      group-id: my-service-group
```

---

## 🛡️ Безопасность

- **JWT-авторизация**  
  Все WebSocket-соединения требуют валидный JWT-токен. Токен может передаваться через заголовок `Authorization: Bearer <token>` или query параметр `?token=<token>`.

- **Валидация токенов**  
  Проверка подписи, срока действия и наличия `userId` в токене.

- **Конфигурация секретного ключа**  
  Секретный ключ настраивается через `jwt.secret-key` (рекомендуется использовать переменную окружения `JWT_SECRET_KEY`).

---

## 🛡️ Масштабируемость (Scalability)

- **Горизонтальное масштабирование**  
  Несколько инстансов `websocket-gateway` могут работать параллельно. Redis Pub/Sub обеспечивает доставку сообщений всем подписанным инстансам.

- **Redis Pub/Sub**  
  Каждый пользователь подписан на свой канал `USER_SUBSCRIPTION:{userId}`, что обеспечивает эффективную маршрутизацию сообщений.

- **Реактивная архитектура**  
  Использование Spring WebFlux и ReactiveRedisTemplate обеспечивает неблокирующую обработку соединений и эффективное использование ресурсов.

---

## 🛡️ Отказоустойчивость (Fault Tolerance)

- **Автоматическое переподключение клиентов**  
  При падении инстанса клиенты могут переподключиться к другому инстансу.

- **Kafka как надежный транспорт**  
  События сохраняются в Kafka, что гарантирует доставку даже при временных проблемах с WebSocket-соединениями.

- **Метрики и мониторинг**  
  Micrometer метрики для отслеживания количества активных соединений (`websocket.connections.total`).

---

## 🎬 Демо

### 1. Запуск окружения

```bash
cd websocket-gateway
.././gradlew clean build
docker-compose up -d
```

Система запускает:
- **Nginx** на порту `8888` как шлюз для WebSocket-подключений
- **websocket-gateway** на порту `8080` (проксируется через Nginx)
- **client-notification** — сервис для интеграции событий
- **simple-payment-microservice** — пример микросервиса с обработкой событий
- **Kafka**, **Redis** и другие зависимости

### 2. Подключение к WebSocket через Nginx

WebSocket-подключения осуществляются через Nginx на порту `8888`. Nginx проксирует запросы к `websocket-gateway` сервису.

Для подключения нужен валидный JWT-токен с `userId` в payload. Пример подключения через wscat:

```bash
wscat -c "ws://localhost:8888/channels" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEyMywic3ViIjoiMTIzIiwiZXhwIjoxNzcwMDM0NTM2LCJpYXQiOjE3Njc0NDI1MzZ9.Y0qaQePaFYrt7dnb0ILLMjC2C1pc_8PNa7uOdH84hgY"
```

После подключения отправьте сообщение:

```json
{
  "target": "DOMAIN",
  "type": "make.payment",
  "userId": 2,
  "payload": {
    "amount": 100.0,
    "cardId": 12345
  }
}
```

### 3. Обработка события в микросервисе

Событие `make.payment` обрабатывается в `simple-payment-microservice` через `MakePaymentHandler`:

```kotlin
@Component
class MakePaymentHandler(
  private val clientNotificationSender: ClientNotificationSender,
) : AbstractClientNotificationEventHandler<MakePaymentPayload>() {
  override val eventType: String = "make.payment"
  override val payloadType = MakePaymentPayload::class.java

  override fun handle(userId: Long, payload: MakePaymentPayload?): Mono<Unit> {
    log.info("Processing make.payment client event=$payload")
    log.info("Decline payment by credit cardId=${payload?.cardId}")
    
    // Отправка ответа клиенту через WebSocket
    return clientNotificationSender.send(
      DomainServerEvent(
        type = DomainServerEventType.PAYMENT_DECLINED_EVENT.type,
        userId = userId,
        payload = PaymentDeclinedPayload("Expired card"),
      )
    )
  }
}
```

### 4. Получение ответа

После обработки события клиент получит ответ через WebSocket:

```json
{
  "type": "payment.declined",
  "userId": 2,
  "payload": {
    "reason": "Expired card"
  }
}
```

**Как это работает:**
1. Клиент отправляет событие `make.payment` через WebSocket
2. `websocket-gateway` маршрутизирует событие в Kafka топик `make.payment`
3. `simple-payment-microservice` обрабатывает событие через `MakePaymentHandler`
4. Микросервис отправляет ответ `payment.declined` через `ClientNotificationSender` в Kafka топик `domain.server.event`
5. `client-notification` получает событие из Kafka и публикует в Redis Pub/Sub канал `USER_SUBSCRIPTION:{userId}`
6. `websocket-gateway` подписан на канал и доставляет сообщение клиенту через WebSocket

### 5. Мониторинг соединений

Проверить метрики через Actuator (напрямую к сервису, минуя Nginx):

```bash
curl http://localhost:8080/actuator/metrics/websocket.connections.total
```

### 6. Проверка статуса пользователя (онлайн/оффлайн)

Количество подписчиков в Redis Pub/Sub соответствует количеству активных соединений пользователя:

```bash
docker exec -it redis redis-cli PUBSUB NUMSUB USER_SUBSCRIPTION:2
```

### 7. Мониторинг через Kafka UI

Откройте Kafka UI в браузере:
```
http://localhost:8088
```

Проверьте топики:
- `make.payment` — события от клиентов для обработки платежей
- `domain.client.event` — все события от клиентов
- `domain.server.event` — события для клиентов (ответы)

---

## 📦 Структура проекта

```
websocket-gateway/
├── websocket-gateway/              # Основной WebSocket gateway сервис
│   ├── src/main/kotlin/com/andver/gateway/websocket/
│   │   ├── WebsocketGatewayApp.kt
│   │   ├── config/
│   │   │   ├── WebSocketConfig.kt
│   │   │   ├── JacksonConfig.kt
│   │   │   └── RedisConfig.kt
│   │   ├── handler/
│   │   │   ├── HandshakeHandler.kt        # JWT авторизация при подключении
│   │   │   └── UserChannelHandler.kt      # Обработка WebSocket сообщений
│   │   ├── service/
│   │   │   ├── AuthorizationService.kt    # Авторизация пользователей
│   │   │   ├── JwtService.kt              # Валидация JWT токенов
│   │   │   ├── UserSubscriptionService.kt # Подписка на Redis Pub/Sub
│   │   │   ├── dispatcher/
│   │   │   │   └── ClientMessageDispatcher.kt
│   │   │   └── processor/
│   │   │       ├── ClientMessageProcessor.kt
│   │   │       ├── domain/
│   │   │       │   └── DomainMessageProcessor.kt
│   │   │       └── system/
│   │   │           ├── SystemMessageProcessor.kt
│   │   │           └── handler/
│   │   │               ├── SystemMessageHandler.kt
│   │   │               └── PingMessageHandler.kt
│   │   └── model/
│   │       ├── InternalClientWebSocketEvent.kt
│   │       └── ClientEventTarget.kt
│   └── src/main/resources/
│       └── application.yml
│
├── client-notification/            # Сервис для интеграции событий
│   ├── src/main/kotlin/com/andver/gateway/client/notification/
│   │   ├── ClientNotificationApp.kt
│   │   ├── config/
│   │   │   ├── JacksonConfig.kt
│   │   │   └── RedisConfig.kt
│   │   ├── listener/
│   │   │   ├── DomainServerEventListener.kt  # Kafka consumer для server events
│   │   │   └── DomainClientEventListener.kt
│   │   ├── processor/
│   │   │   ├── DomainServerEventProcessor.kt # Публикация в Redis Pub/Sub
│   │   │   └── DomainClientEventProcessor.kt
│   │   └── model/
│   │       └── InternalDomainEvent.kt
│   └── src/main/resources/
│       └── application.yml
│
├── client-notification-model/      # Модели данных (DomainServerEvent, DomainClientEvent)
│   └── src/main/kotlin/com/andver/client/notification/model/
│       ├── server/
│       │   └── DomainServerEvent.kt
│       └── client/
│           ├── DomainClientEvent.kt
│           └── DomainClientEventType.kt
│
├── client-notification-sender-starter/    # Spring Boot Starter для отправки событий
│   ├── src/main/kotlin/com/andver/client/notification/sender/
│   │   ├── ClientNotificationSender.kt
│   │   └── ClientNotificationSenderAutoConfiguration.kt
│   └── src/main/resources/META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── client-notification-consumer-starter/  # Spring Boot Starter для обработки событий
│   ├── src/main/kotlin/com/andver/client/notification/
│   │   ├── ClientNotificationConsumer.kt
│   │   └── handler/
│   │       └── ClientNotificationEventHandler.kt
│   └── src/main/resources/META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── simple-payment-microservice/    # Пример микросервиса с обработкой событий
│   └── src/main/kotlin/com/andver/example/client/notification/payment/
│       └── SimplePaymentMicroserviceApp.kt
│
├── docker-compose.yml              # Конфигурация Docker Compose
├── nginx/
│   └── nginx.conf                  # Nginx конфигурация для проксирования
└── README.md
```



