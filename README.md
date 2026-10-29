# Turf Booking

| Service         | Port (default) | Paths it serves                                   |
|-----------------|----------------|---------------------------------------------------|
| api-gateway     | **8000**       | everything below, in one place                    |
| auth-service    | 8080           | `/users/**`                                       |
| turf-service    | 8082           | `/turfs/**`, `/sports`                            |
| owner-service   | 8083           | `/owners/**`                                      |
| booking-service | 8084           | `/bookings/**`                                    |

## API for the frontend

Call **only the gateway** (`http://localhost:8000`). Paths are unchanged from the services (no prefix is added or stripped).
Send the JWT from login as `Authorization: Bearer <token>`; the gateway forwards it and each service still checks it and the
caller's role itself, so protection is exactly what it was before.

| Method | Path | Who |
|---|---|---|
| POST | `/users/create`, `/users/login` | public |
| GET | `/users/{email}` | logged in (own account only) |
| GET | `/sports`, `/turfs`, `/turfs/{id}` | public |
| GET | `/turfs/mine` | OWNER |
| GET | `/turfs/{id}/timeslots[?date=yyyy-MM-dd]` | CUSTOMER, OWNER |
| POST | `/turfs`, `/turfs/courts/{courtNumber}/timeslots` | OWNER |
| PUT | `/turfs/{id}`, `/turfs/{turfId}/timeslots/{slotId}/status` | OWNER |
| DELETE | `/turfs/{id}` | OWNER, ADMIN |
| POST / GET / GET | `/owners`, `/owners/me`, `PUT /owners/me` | OWNER |
| PUT | `/owners/{ownerId}/verification` | ADMIN |
| POST, GET, GET, PUT | `/bookings`, `/bookings/{id}`, `/bookings`, `/bookings/{id}` | CUSTOMER |
| GET | `/bookings/owner` | OWNER |
| GET | `/actuator/health` (gateway itself) | public |

Gateway behaviour: unknown paths get 404 from the gateway; if a service is down the gateway answers a JSON `503`
(`504` on timeout). CORS is answered by the gateway for the origins in `CORS_ALLOWED_ORIGINS`
(allowed headers: `Authorization`, `Content-Type`, `Idempotency-Key`, `Accept`).

## Configuration

All environment-specific values and secrets live in the root `.env` (git-ignored). `.env.example` is the template:

```
cp .env.example .env      # then set JWT_SECRET, passwords, ...  (openssl rand -base64 48)
```

How `.env` gets read:

* **Spring services (Maven or IDE):** each `application.properties` has
  `spring.config.import=optional:file:./.env[.properties],optional:file:../.env[.properties]`, so the file is read when the
  working directory is the repo root or a service folder. No extra library is needed. Format is `KEY=VALUE`, no quotes.
  Real environment variables override the file.
* **VS Code:** `.vscode/launch.json` also passes it as `envFile`. For IntelliJ, use *Run configuration → EnvFile / Environment
  variables* or rely on the Spring import above (set the working directory to the repo root).
* **Docker Compose:** `docker compose` loads the root `.env` automatically.

RabbitMQ exchange, queue, DLQ and routing-key names are the `app.rabbit.*` properties in each service, fed by the variables in
`.env.example` (a default equal to the current name is built in). A name used by two services must be changed in both.

## Run locally

Prerequisites: JDK 21, PostgreSQL on `localhost:5432` with databases `turf_auth`, `turf_service`, `turf_owner`,
`turf_booking`, and RabbitMQ.

```
cp .env.example .env            # edit it
docker compose up -d rabbitmq   # RabbitMQ with the user from .env (or use your own broker and set RABBITMQ_*)
(cd auth-service    && ./mvnw spring-boot:run) &
(cd owner-service   && ./mvnw spring-boot:run) &
(cd turf-service    && ./mvnw spring-boot:run) &
(cd booking-service && ./mvnw spring-boot:run) &
(cd api-gateway     && ./mvnw spring-boot:run)
```

Or in VS Code run the *all services + gateway* launch configuration.
The first admin is created from `ADMIN_EMAIL` / `ADMIN_PASSWORD` when both are set.

Tests: `./mvnw test` inside each service folder.
