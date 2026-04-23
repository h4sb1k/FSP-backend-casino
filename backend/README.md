# FSP Casino — Быстрые игровые комнаты Столото VIP

Backend-сервис быстрых игровых комнат на бонусные баллы для VIP-аудитории Столото.
Пользователь выбирает комнату, резервирует баллы, при желании покупает буст и дожидается результата раунда.
Победитель определяется взвешенным генератором случайных чисел — результат прозрачен и верифицируем через лог.

---

## Архитектура

Проект собран как **multi-module Maven** с тремя слоями:

```
┌─────────────────────────────────────────────────┐
│          Spring Boot Application (casino-app)   │
│  ┌──────────────────┐   ┌──────────────────────┐│
│  │  casino-domain   │   │    casino-game        ││
│  │  JPA entities    │◄──│  WinnerService        ││
│  │  Repositories    │   │  BalanceService       ││
│  │  Enums           │   │  BotService           ││
│  └──────────────────┘   │  RoundScheduler       ││
│                         └──────────────────────┘│
│  Controllers · Security · WebSocket · Flyway     │
└──────────────────┬──────────────────────────────┘
                   │
         ┌─────────┴──────────┐
         │                    │
   PostgreSQL 16          Redis 7
   порт 5432              порт 6379
   (схема, данные)        (таймеры комнат)
```

**casino-domain** — JPA-сущности и Spring Data репозитории, никакой бизнес-логики.

**casino-game** — вся игровая механика: определение победителя, баланс, боты, планировщик раундов. Модуль не зависит от HTTP-слоя и тестируется изолированно.

**casino-app** — точка входа: REST-контроллеры, Spring Security + JWT, WebSocket (заглушка), Flyway-миграции, OpenAPI.

---

## Быстрый старт

### Требования

- Docker и Docker Compose v2 (`docker compose version`)
- Ничего больше не нужно — Java и Maven внутри контейнера

### Запуск

```bash
# 1. Скопировать переменные окружения
cp .env.example .env

# 2. ОБЯЗАТЕЛЬНО: заменить JWT_SECRET в .env на строку длиной ≥ 32 символа
#    Пример: JWT_SECRET=my-super-secret-key-for-fsp-casino-2026!

# 3. Запустить все сервисы
docker compose up --build
```

После старта (~30 сек):

| Сервис | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui |
| Health check | http://localhost:8080/actuator/health |
| API | http://localhost:8080/api |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |

### Остановка

```bash
docker compose down        # остановить, данные сохраняются
docker compose down -v     # остановить и удалить данные БД
```

---

## Тестовые пользователи

Все пользователи созданы при старте через `V2__seed_data.sql`. Пароль для всех: **`password`**

| username | password | vipTier | balance | role |
|---|---|---|---|---|
| aleksey_m | password | GOLD | 50 000 | USER |
| vip_player | password | PLATINUM | 200 000 | **ADMIN** |
| lucky77 | password | SILVER | 15 000 | USER |
| new_player | password | STANDARD | 3 000 | USER |
| pro_gamer | password | GOLD | 75 000 | USER |

`vip_player` — единственный администратор. Только он может обращаться к `/api/admin/**`.

При старте также создаются **4 тестовые комнаты**:

| ID | Тир | Мест | Вход | Фонд | Буст |
|---|---|---|---|---|---|
| 1 | STANDARD | 4 | 100 pts | 80% | 50 pts (×2.0) |
| 2 | SILVER | 6 | 500 pts | 80% | 200 pts (×2.0) |
| 3 | GOLD | 8 | 2 000 pts | 75% | 800 pts (×2.5) |
| 4 | STANDARD | 6 | 200 pts | 80% | отключён |

---

## API Reference

### Аутентификация

Получить токен → передавать в заголовке всех защищённых запросов.

```bash
# Получить JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"aleksey_m","password":"password"}' | jq -r .token)

# Использовать в запросах
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/auth/me
```

### Публичные эндпоинты (без токена)

- `POST /api/auth/login`
- `GET /api/rooms`
- `GET /api/rooms/{id}`
- `GET /swagger-ui/**`
- `GET /v3/api-docs/**`
- `GET /actuator/health`
- `WS /ws/**`

### Все эндпоинты

#### Auth — `/api/auth`

| Метод | Путь | Описание | Auth |
|---|---|---|---|
| POST | `/api/auth/login` | Получить JWT-токен | Нет |
| GET | `/api/auth/me` | Текущий пользователь | JWT |

**POST /api/auth/login**
```json
// Запрос
{ "username": "aleksey_m", "password": "password" }

// Ответ 200
{
  "token": "eyJ...",
  "userId": 1,
  "username": "aleksey_m",
  "vipTier": "GOLD",
  "balance": 50000,
  "reservedBalance": 0
}

// Ошибка 401
{ "error": "Invalid credentials", "code": "AUTH_FAILED" }
```

---

#### Rooms — `/api/rooms`

| Метод | Путь | Описание | Auth |
|---|---|---|---|
| GET | `/api/rooms` | Список комнат с фильтрами | Нет |
| GET | `/api/rooms/{roomId}` | Детали комнаты | Нет |
| POST | `/api/rooms` | Создать комнату | JWT |
| POST | `/api/rooms/{roomId}/join` | Войти в комнату | JWT |
| POST | `/api/rooms/{roomId}/leave` | Выйти из комнаты | JWT |
| POST | `/api/rooms/{roomId}/boost` | Купить буст | JWT |

**GET /api/rooms** — query-параметры (все опциональны):

| Параметр | Тип | Описание |
|---|---|---|
| `entryFeeMin` | Long | Минимальная стоимость входа |
| `entryFeeMax` | Long | Максимальная стоимость входа |
| `seatsMin` | Integer | Минимум свободных мест |
| `seatsMax` | Integer | Максимум свободных мест |
| `tier` | String | STANDARD / SILVER / GOLD |
| `status` | String | WAITING / RUNNING / FINISHED / CANCELLED |

Без `status` — возвращает WAITING и RUNNING.

**POST /api/rooms** — тело запроса:
```json
{
  "tier": "STANDARD",
  "maxSlots": 6,
  "entryFee": 100,
  "prizePoolPct": 80,
  "boostEnabled": true,
  "boostCost": 50,
  "boostMultiplier": 2.0
}
```

**POST /api/rooms/{roomId}/join** — тело пустое `{}`, `userId` берётся из JWT.

**POST /api/rooms/{roomId}/boost** — ответ:
```json
{
  "newWinProbability": 0.2857,
  "boostMultiplier": 2.0,
  "boostCostPaid": 50
}
```

---

#### Users — `/api/users`

| Метод | Путь | Описание | Auth |
|---|---|---|---|
| GET | `/api/users/{userId}/active-room` | Активная комната пользователя | JWT |
| GET | `/api/users/{userId}/profile` | Профиль и история участий | JWT |

**GET /api/users/{userId}/profile** — query: `limit` (по умолчанию 20).

> При запросе чужого профиля поля `balance` и `reservedBalance` возвращаются как `null`.

---

#### History — `/api/history`

| Метод | Путь | Описание | Auth |
|---|---|---|---|
| GET | `/api/history` | История раундов | JWT |

Query-параметры: `limit` (по умолчанию 50, максимум 200), `roomId` (опционально).

Ответ содержит поля `rngRoll` и `rngTotalWeight` для верификации честности RNG.

---

#### Admin — `/api/admin`

> Только для пользователей с ролью `ADMIN` (vip_player).

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/admin/config` | Текущая конфигурация |
| POST | `/api/admin/config/validate` | Проверить конфигурацию |
| POST | `/api/admin/config` | Сохранить конфигурацию |
| GET | `/api/admin/rooms` | Все комнаты включая FINISHED |
| PUT | `/api/admin/rooms/{roomId}/config` | Изменить параметры комнаты (только WAITING) |

**POST /api/admin/config/validate** — ответ:
```json
{
  "valid": true,
  "errors": [],
  "warnings": ["prizePoolPct ниже 60% — комната непривлекательна"],
  "estimatedHouseEdge": 0.20,
  "estimatedOperatorRoi": 0.25
}
```

Правила валидации:

| Уровень | Условие |
|---|---|
| ERROR | `maxSlots` < 2 или > 10 |
| ERROR | `entryFee` ≤ 0 |
| ERROR | `prizePoolPct` < 0 или > 100 |
| WARNING | `prizePoolPct` < 60 |
| WARNING | `100 - prizePoolPct` < 5 (маржа оператора < 5%) |
| WARNING | `boostMultiplier` > 4.0 |
| WARNING | `boostCost` > `entryFee` × 0.8 |
| WARNING | `waitingTimerSeconds` < 10 |

---

### Формат ошибок

Все ошибки возвращаются в едином формате:

```json
{
  "error": "Human-readable message",
  "code": "MACHINE_READABLE_CODE",
  "timestamp": "2026-04-22T10:15:30Z"
}
```

| HTTP | code | Когда |
|---|---|---|
| 401 | UNAUTHORIZED | Нет или невалидный JWT |
| 401 | AUTH_FAILED | Неверный username/password |
| 403 | ACCESS_DENIED | Нет роли ADMIN |
| 404 | ROOM_NOT_FOUND | Комната не существует |
| 409 | ROOM_NOT_JOINABLE | Комната полная или не в статусе WAITING |
| 422 | INSUFFICIENT_BALANCE | Недостаточно баллов |
| 500 | INTERNAL_ERROR | Внутренняя ошибка |

---

## WebSocket

### Подключение (работает сейчас)

STOMP endpoint: `ws://localhost:8080/ws` (с SockJS fallback).

```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  onConnect: () => {
    // Подписаться на события комнаты
    client.subscribe('/topic/room/1', (msg) => {
      console.log(JSON.parse(msg.body));
    });

    // Проверить соединение — ping/pong
    client.publish({ destination: '/app/room/1/ping' });
    // Ответ: { "type": "PONG", "roomId": "1", "timestamp": 1234567890 }
  }
});
client.activate();
```

### Планируемые события (TODO)

Когда WebSocket будет полностью реализован, на топик `/topic/room/{roomId}` будут приходить:

| Тип события | Ключевые поля | Когда |
|---|---|---|
| `PARTICIPANT_UPDATE` | `participants`, `seatsFilled`, `seatsTotal` | join / leave |
| `TIMER_TICK` | `secondsRemaining` | каждую секунду |
| `BOTS_FILLED` | `botsAdded`, `participants` | после истечения таймера |
| `ROUND_STARTED` | `animationDurationMs` | старт раунда |
| `ROUND_RESULT` | `winnerParticipantId`, `winnerIsBot`, `payout`, `winningCombination` | конец раунда |
| `BALANCE_UPDATE` | `userId`, `newBalance` | после settle |

На топик `/topic/rooms-list` — событие `ROOM_CREATED` при создании новой комнаты.

> Сейчас вместо реальных broadcast-событий методы `RoomEventPublisher` пишут в лог. Переключить поведение можно заменив `log.info(...)` на `messagingTemplate.convertAndSend(...)`.

---

## Игровая механика

### Полный цикл раунда

1. Пользователь вызывает `POST /api/rooms/{id}/join` → баллы резервируются (`balance -= entryFee`, `reservedBalance += entryFee`)
2. Запускается таймер: `timerStartedAt = NOW()`, TTL сохраняется в Redis (`room:timer:{id}`)
3. По желанию — `POST /api/rooms/{id}/boost` → `balance -= boostCost`, участник помечается `boosted = true`
4. `RoundScheduler` каждые 5 секунд ищет WAITING-комнаты, у которых `timerStartedAt + 60s < NOW()`
5. Свободные места заполняются ботами (`BotService`)
6. Комната переходит в статус `RUNNING`
7. `WinnerService` определяет победителя взвешенным RNG
8. `BalanceService.settle()` — `reservedBalance` обнуляется у всех, победитель получает `payout`
9. В `round_history` сохраняются параметры RNG для аудита
10. Комната переходит в статус `FINISHED`

### Алгоритм определения победителя

```
weight(participant) = boostMultiplier   если participant.boosted == true
weight(participant) = 1.0               иначе

totalWeight = сумма весов всех участников (реальные + боты)
roll        = random(0, totalWeight)

Победитель: первый участник у которого накопленный cursor >= roll
```

**Пример расчёта вероятности** (6 мест, `boostMultiplier = 2.0`):

```
Без буста:  1 / (5×1.0 + 1.0) = 1/6  ≈ 16.7%
С бустом:   2 / (5×1.0 + 2.0) = 2/7  ≈ 28.6%
```

Поля `rngRoll` и `rngTotalWeight` сохраняются в `round_history` — победитель верифицируем без дополнительных запросов.

### Боты

- Заполняют пустые места после истечения таймера ожидания
- Имена генерируются из пула русских имён + суффикс 10–99: `Борис_42`, `Елена_17`
- В API видны как `{ "isBot": true, "botName": "Борис_42", "userId": null }`
- Боты участвуют в RNG наравне с реальными игроками
- Если бот победил — `payout` остаётся у оператора, ни один реальный игрок его не получает

### Баланс

| Операция | Метод | Когда | Эффект |
|---|---|---|---|
| Резервирование | `reserve` | join, createRoom | `balance -= X`, `reservedBalance += X` |
| Возврат | `release` | leave | `balance += X`, `reservedBalance -= X` |
| Прямое списание | `deduct` | покупка буста | `balance -= boostCost` |
| Расчёт | `settle` | конец раунда | `reservedBalance` обнуляется; победитель получает `payout = totalPool × prizePoolPct / 100` |

---

## Схема базы данных

**`users`** — пользователи системы
- `id`, `username` (уникальный), `passwordHash` (BCrypt strength 12)
- `vipTier`: STANDARD / SILVER / GOLD / PLATINUM
- `role`: USER / ADMIN
- `balance` — доступные баллы
- `reservedBalance` — зарезервированные (в активных комнатах)

**`rooms`** — игровые комнаты
- `status`: WAITING → RUNNING → FINISHED (или CANCELLED)
- `tier`: STANDARD / SILVER / GOLD
- `maxSlots` (2–10), `entryFee`, `prizePoolPct`
- `boostEnabled`, `boostCost`, `boostMultiplier`
- `timerStartedAt` — момент запуска таймера ожидания
- `winnerParticipantId` — FK на `room_participants` после раунда

**`room_participants`** — участники комнат
- `roomId` FK → `rooms`, `userId` FK → `users` (NULL для ботов)
- `isBot`, `botName`, `boosted`
- Уникальный constraint: `(roomId, userId)` — один пользователь в одной комнате

**`round_history`** — аудит-лог раундов
- `winnerIsBot`, `winnerUserId`, `winnerParticipantId`
- `totalPool`, `payout`
- `rngSeed`, `rngRoll`, `rngTotalWeight` — для верификации честности
- `participantCount`, `botCount`

**`admin_config`** — конфигурация (одна запись, `id = 1`)
- `defaultMaxSlots`, `defaultEntryFee`, `defaultPrizePoolPct`
- `defaultBoostEnabled`, `defaultBoostCost`, `defaultBoostMultiplier`
- `waitingTimerSeconds` (по умолчанию 60)

---

## Структура проекта

```
backend/
├── docker-compose.yml
├── Dockerfile                          # multi-stage: Maven → JRE Alpine
├── .env.example
├── pom.xml                             # parent POM, Spring Boot 3.2.5
│
├── casino-domain/                      # Модуль 1: доменная модель
│   └── src/main/java/ru/fsp/casino/domain/
│       ├── model/                      # User, Room, RoomParticipant, RoundHistory, AdminConfig
│       ├── repository/                 # Spring Data JPA interfaces
│       └── enums/                      # RoomStatus, VipTier, UserRole
│
├── casino-game/                        # Модуль 2: игровая логика
│   └── src/
│       ├── main/java/ru/fsp/casino/game/service/
│       │   ├── WinnerService.java      # взвешенный RNG
│       │   ├── BalanceService.java     # reserve / release / deduct / settle
│       │   ├── BotService.java         # заполнение ботами
│       │   └── RoundScheduler.java     # @Scheduled, запуск раундов
│       └── test/java/ru/fsp/casino/game/
│           ├── WinnerServiceTest.java  # 5 unit-тестов
│           └── BalanceServiceTest.java # 6 unit-тестов
│
└── casino-app/                         # Модуль 3: HTTP-слой
    └── src/main/
        ├── java/ru/fsp/casino/app/
        │   ├── config/                 # Security, JWT, WebSocket, OpenAPI, Jackson
        │   ├── controller/             # Auth, Room, User, Admin, History, WebSocketStub
        │   ├── dto/                    # Java Records для всех запросов/ответов
        │   ├── security/               # JwtTokenProvider, JwtAuthenticationFilter
        │   ├── websocket/              # RoomEventPublisher (заглушка)
        │   └── exception/              # GlobalExceptionHandler, кастомные исключения
        └── resources/
            ├── application.yml
            └── db/migration/
                ├── V1__init_schema.sql
                └── V2__seed_data.sql
```

---

## Переменные окружения

| Переменная | Пример | Описание | Менять в проде |
|---|---|---|---|
| `POSTGRES_DB` | `fsp_casino` | Имя базы данных | По желанию |
| `POSTGRES_USER` | `casino_user` | Пользователь PostgreSQL | По желанию |
| `POSTGRES_PASSWORD` | `casino_pass_change_me` | Пароль PostgreSQL | **Обязательно** |
| `JWT_SECRET` | `your-secret-min-32-chars!!` | Секрет подписи JWT-токенов | **Обязательно** |
| `JWT_EXPIRATION_MS` | `86400000` | Время жизни токена (мс). По умолчанию 24 ч | По желанию |

---

## Тесты

### Запуск

```bash
# Все тесты
cd backend && mvn test

# Только casino-game (без Docker/БД)
mvn test -pl casino-game
```

### Что покрыто

**WinnerServiceTest** (5 тестов):
- `determineWinner_allEqualWeights_uniformDistribution` — 1000 прогонов, все 6 участников побеждают хотя бы раз
- `determineWinner_withBoost_higherWinRate` — участник с бустом побеждает > 25% в 1000 прогонах
- `determineWinner_singleParticipant_alwaysWins` — единственный участник всегда победитель
- `calculateWinProbability_withBoost_returnsCorrectValue` — 6 мест, boost×2.0 → 2/7 ≈ 0.2857
- `calculateWinProbability_withoutBoost_returnsCorrectValue` — 6 мест → 1/6 ≈ 0.1667

**BalanceServiceTest** (6 тестов):
- `reserve_sufficientBalance_deductsFromAvailable` — balance 1000, reserve 500 → balance 500, reserved 500
- `reserve_insufficientBalance_throwsException` — balance 1000, reserve 1500 → InsufficientBalanceException
- `release_returnsToAvailable` — возврат при выходе из комнаты
- `settle_realWinner_creditsBalance` — 4 игрока × 100 pts, 80% фонд → победитель получает 320 pts
- `settle_botWinner_noBalanceChange` — бот победил → никто не получает баллы
- `deduct_sufficientBalance_deductsDirectly` — баланс минус boostCost, reservedBalance не трогается

---

## Демо-сценарии

Все команды выполняются последовательно. Требуется `jq`.

### Сценарий 1. Логин и просмотр комнат

```bash
# Получить токен
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"aleksey_m","password":"password"}' | jq -r .token)

echo "Token получен: ${TOKEN:0:20}..."

# Посмотреть профиль — баланс 50 000 pts
curl -s http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq '{username, balance, reservedBalance}'

# Список WAITING-комнат (публично, без токена)
curl -s http://localhost:8080/api/rooms | jq '[.[] | {id, tier, entryFee, seatsFilled, maxSlots}]'
```

### Сценарий 2. Войти в комнату и купить буст

```bash
# Войти в комнату #1 (STANDARD, 100 pts)
curl -s -X POST http://localhost:8080/api/rooms/1/join \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}' | jq '{id, status, seatsFilled, maxSlots}'

# Проверить баланс — должен уменьшиться на 100
curl -s http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq '{balance, reservedBalance}'

# Купить буст (50 pts) — проверить новую вероятность победы
curl -s -X POST http://localhost:8080/api/rooms/1/boost \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}' | jq '{newWinProbability, boostMultiplier, boostCostPaid}'
```

### Сценарий 3. Дождаться результата

```bash
# Ждать ~60 секунд (waitingTimerSeconds из конфига)
# Планировщик проверяет каждые 5 секунд — максимальная задержка 65 сек

# Проверить статус комнаты
curl -s http://localhost:8080/api/rooms/1 | jq '{status, seatsFilled, maxSlots}'

# После FINISHED — посмотреть результат в истории
curl -s "http://localhost:8080/api/history?roomId=1&limit=1" \
  -H "Authorization: Bearer $TOKEN" | jq '.[0]'
```

### Сценарий 4. Верификация честности RNG

```bash
# В ответе истории есть rngRoll и rngTotalWeight
# Победитель — участник, у которого накопленный cursor >= rngRoll
# Это можно проверить вручную по списку участников комнаты

curl -s "http://localhost:8080/api/history?limit=1" \
  -H "Authorization: Bearer $TOKEN" \
  | jq '.[0] | {winnerDisplayName, winnerIsBot, rngRoll, rngTotalWeight, totalPool, payout}'

# Пример: rngRoll=1.23, rngTotalWeight=4.0
# При 4 участниках без буста (вес по 1.0) — победил третий участник (cursor: 1→2→3 >= 1.23? нет... 2.0 >= 1.23? да → второй)
```

### Сценарий 5. Admin-конфигурация

```bash
# Войти как администратор
ADMIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"vip_player","password":"password"}' | jq -r .token)

# Текущая конфигурация
curl -s http://localhost:8080/api/admin/config \
  -H "Authorization: Bearer $ADMIN" | jq .

# Проверить конфигурацию с коротким таймером
curl -s -X POST http://localhost:8080/api/admin/config/validate \
  -H "Authorization: Bearer $ADMIN" \
  -H "Content-Type: application/json" \
  -d '{
    "defaultMaxSlots": 6,
    "defaultEntryFee": 100,
    "defaultPrizePoolPct": 80,
    "defaultBoostEnabled": true,
    "defaultBoostCost": 50,
    "defaultBoostMultiplier": 2.0,
    "waitingTimerSeconds": 10
  }' | jq '{valid, warnings, estimatedHouseEdge}'

# Сохранить — раунды будут запускаться через 10 секунд
curl -s -X POST http://localhost:8080/api/admin/config \
  -H "Authorization: Bearer $ADMIN" \
  -H "Content-Type: application/json" \
  -d '{
    "defaultMaxSlots": 6,
    "defaultEntryFee": 100,
    "defaultPrizePoolPct": 80,
    "defaultBoostEnabled": true,
    "defaultBoostCost": 50,
    "defaultBoostMultiplier": 2.0,
    "waitingTimerSeconds": 10
  }' | jq .

# Проверить что обычный пользователь не может — должен получить 403
curl -s http://localhost:8080/api/admin/config \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

## Технический стек

| Компонент | Версия |
|---|---|
| Java | 17 (Dockerfile: JRE 21) |
| Spring Boot | 3.2.5 |
| PostgreSQL | 16 |
| Redis | 7 |
| JJWT | 0.12.6 |
| SpringDoc OpenAPI | 2.5.0 |
| Flyway | 10.15.2 |
| Lombok | 1.18.32 |

---

## Известные ограничения (TODO)

- **WebSocket события** — `RoomEventPublisher` сейчас пишет события в лог вместо реального broadcast. Работает только ping/pong через `/app/room/{id}/ping` → `/topic/room/{id}`
- **`waitingTimerSeconds` из конфига** — планировщик читает значение при старте (`@Value`), изменение через `/api/admin/config` вступит в силу только после перезапуска приложения
- **Один пользователь — одна активная комната** — нельзя одновременно быть в двух WAITING/RUNNING комнатах
- **Нет регистрации** — только логин существующих пользователей из seed-данных
