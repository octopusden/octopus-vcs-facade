# deferred-result

Лёгкая библиотека для "at most once" выполнения операций в приложениях из нескольких инстансов.

Позволяет запускать тяжёлую операцию **ровно один раз** по всему кластеру.
Если ключ уже есть — операция не запускается, а сразу отдаётся сохранённый результат
(даже если он был посчитан на другом инстансе).

## Основная идея

- Тяжёлая операция (например, анализ репозитория) выполняется только на одном инстансе.
- Все остальные инстансы либо ждут результата, либо сразу получают его из хранилища.
- Нет дублирования работы.

## Основной API

```kotlin
val deferred = deferredResultManager.execute(requestId, String::class.java) {
    // тяжёлая операция
    vcsManager.getRepository(sshUrl)
}

val result = deferredResultManager.get(requestId, String::class.java)
```

## Автоконфигурация (Spring Boot)

```yaml
deferred-result:
  enabled: true
  namespace: "vcs-facade:deferred-result"
  processing-ttl: 15m
  result-ttl: 15m
  valkey:
    host: localhost
    port: 6379
```

## Важные моменты

- Операция захватывается атомарно через Valkey/Redis (setIfAbsent).
- Используется `ownerId` + `compareAndSet` для защиты от race conditions.
- Для хранения результата и "захвата" используются два TTL параметра.
- Если операция ещё выполняется — возвращается статус `PROCESSING`.
