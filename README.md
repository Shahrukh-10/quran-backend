# Quran Backend (Spring Boot)

Spring Boot 3.3 backend for the Islamic website — sibling to the Next.js app in `../`.

## Stack

- **Java 21**, Kotlin 1.9
- **Spring Boot 3.3.4** (web, data-jpa, validation, cache, thymeleaf, security, actuator)
- **H2** file database (dev), Flyway for migrations
- **springdoc-openapi** for Swagger UI
- **Gradle 8.10** (Kotlin DSL) with wrapper

## Run

```bash
./gradlew bootRun
```

Server starts on **http://localhost:8080**.

- Health check: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- Swagger UI: http://localhost:8080/swagger-ui.html

## Database

H2 file DB persisted at `backend/data/quran-h2.mv.db` (with `AUTO_SERVER=TRUE` so multiple JVMs can share it in dev).

Flyway migrations run automatically on startup from `src/main/resources/db/migration/`. Currently empty — added in later tasks.

## Build

```bash
./gradlew build          # compile + test + package
./gradlew build -x test  # skip tests
./gradlew bootJar        # runnable jar → build/libs/
```

## Security

`SecurityConfig` currently permits all requests. Basic auth on `/admin/**` will land in task SB-7.
