# Explore With Me (microservices)

## Запуск
Краткая шпаргалка, порядок важен: сначала инфраструктура, затем прикладные сервисы.

1. Подготовьте БД для `main` и `stats` (PostgreSQL). В `docker-compose.yml` есть пример окружения.
2. Соберите проект: `mvn -q -DskipTests clean package`.
3. Запустите сервисы (каждый в отдельном терминале).

Команды запуска:
- `java -jar infra/discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar`
- `java -jar infra/config-server/target/config-server-0.0.1-SNAPSHOT.jar`
- `java -jar stat/stat-server/target/stats-server-0.0.1-SNAPSHOT.jar`
- `java -jar core/event-service/target/event-service-0.0.1-SNAPSHOT.jar`
- `java -jar core/user-service/target/user-service-0.0.1-SNAPSHOT.jar`
- `java -jar core/request-service/target/request-service-0.0.1-SNAPSHOT.jar`
- `java -jar core/extra-service/target/extra-service-0.0.1-SNAPSHOT.jar`
- `java -jar infra/gateway-server/target/gateway-server-0.0.1-SNAPSHOT.jar`

Проверка:
- Eureka: `http://localhost:8761`
- Gateway: `http://localhost:8080`


## Архитектура
Система разложена на инфраструктурные сервисы и прикладные микросервисы:
- `discovery-server` — Eureka для регистрации и обнаружения сервисов.
- `config-server` — централизованная конфигурация (Spring Cloud Config).
- `gateway-server` — единая точка входа (Spring Cloud Gateway, порт 8080).
- `event-service` — основной домен: мероприятия и связанные операции.
- `user-service` — администрирование пользователей (прокси к `event-service` через Feign).
- `request-service` — заявки на участие (прокси к `event-service` через Feign).
- `extra-service` — категории и подборки (прокси к `event-service` через Feign).
- `stats-server` — сервис статистики.

## Конфигурация
Конфиги сервисов находятся в:
- `infra/config-server/src/main/resources/config`

Файлы:
- `event-service.properties`
- `user-service.properties`
- `request-service.properties`
- `extra-service.properties`
- `gateway-server.properties`
- `stats-server.properties`

## Внутреннее API
Межсервисное взаимодействие:
- `user-service` -> `event-service` (Feign)
- `request-service` -> `event-service` (Feign)
- `extra-service` -> `event-service` (Feign)
- `event-service` -> `stats-server` (stat-client)

Сервис Discovery (Eureka) используется для поиска адресов сервисов, Config Server — для конфигурации.

## Внешнее API
Спецификации:
- `ewm-main-service-spec.json`
- `ewm-stats-service-spec.json`
