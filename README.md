# Event Ticket Booking Service

A small REST service for booking event seats. A booking holds its seats for 5 minutes. If it is not confirmed in that time, the seats are released. The main thing it is built to get right: **two people can never end up with the same seat**, even when their requests arrive at the same moment.

Built with Java 17, Spring Boot 3, Spring Data JPA (Hibernate), PostgreSQL, Maven, JUnit 5, Mockito and Docker Compose.

## Run it

With Docker (app + PostgreSQL):

```bash
docker compose up --build
```

The API is then on `http://localhost:8080`.

Without Docker you need JDK 17+, Maven and a PostgreSQL database named `ticketbooking` (user `ticket`, password `ticket`; override with `DB_URL`, `DB_USER`, `DB_PASSWORD`):

```bash
mvn spring-boot:run
```

Run the tests (no database needed, they use in-memory H2):

```bash
mvn test
```

## API

| Method | Path | What it does |
| --- | --- | --- |
| POST | `/events` | Create an event with `seatCount` seats (labels `S1`, `S2`, ...) |
| GET | `/events/{id}` | Event with total and available seat counts |
| GET | `/events/{id}/seats` | All seats and their status |
| POST | `/events/{id}/bookings` | Hold seats. Optional `Idempotency-Key` header |
| GET | `/bookings/{id}` | Booking status |
| POST | `/bookings/{id}/confirm` | Confirm (stands in for a successful payment) |
| POST | `/bookings/{id}/cancel` | Cancel a pending booking and release its seats |

Status codes: `201` created, `200` ok or idempotent replay, `400` invalid input, `404` not found, `409` conflict (seat taken, hold expired, illegal state change, concurrent update), `422` idempotency key reused with a different request.

Example:

```bash
# 1. create an event with 10 seats
curl -s -X POST localhost:8080/events -H 'Content-Type: application/json' \
  -d '{"name":"Jazz Night","seatCount":10}'

# 2. look at the seats, then hold seats 1 and 2 (use the ids from step 2)
curl -s localhost:8080/events/1/seats
curl -s -X POST localhost:8080/events/1/bookings \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: order-123' \
  -d '{"seatIds":[1,2]}'

# 3. sending the same request again returns the same booking (200), not a second one
# 4. confirm it
curl -s -X POST localhost:8080/bookings/1/confirm
```

## How a booking works

```
PENDING --confirm--> CONFIRMED
   |--cancel-------> CANCELLED
   '--hold runs out-> EXPIRED
```

`CONFIRMED`, `CANCELLED` and `EXPIRED` are final. The rules live in `BookingStatus` and `Booking`, so an illegal change (confirming twice, confirming an expired hold) is rejected in one place.

## Design choices

- **Layers: controller, service, repository.** Controllers handle HTTP only, services hold the business rules and the transactions, repositories talk to the database. Entities are never returned directly; services map them to DTO records.
- **Optimistic locking (`@Version` on `Seat` and `Booking`).** Every update runs as `UPDATE ... WHERE id = ? AND version = ?`. If two requests read the same seat and both try to change it, the second update matches no row and fails instead of overwriting. Conflicts are rare for most seats, so this avoids holding database locks. For one very popular seat, pessimistic locking (`SELECT ... FOR UPDATE`) or an atomic `UPDATE ... WHERE status = 'AVAILABLE'` would be the alternative.
- **All or nothing.** Holding several seats happens in one transaction. If one seat is taken, nothing is held.
- **Database constraints as a backstop.** The join table has a unique constraint on `seat_id`, so the database itself refuses to attach one seat to two bookings, whatever the application code does. The idempotency key column is unique too.
- **Idempotent bookings.** A client that retries after a timeout sends the same `Idempotency-Key` and gets the original booking back. The service stores a hash of (event, seats) with the key, so reusing a key for a different request is rejected with `422`.
- **Consistent seat order.** Seats are always loaded and updated in id order, so two bookings that overlap lock rows in the same order.
- **Injected `Clock`.** All time logic reads from a `Clock` bean. Tests replace it with one they can move forward, so expiry is tested without sleeping.
- **Expiry job.** A `@Scheduled` task releases holds that ran out, every 30 seconds. A confirm after the hold ends is rejected even if the job has not run yet.
- **One error format.** `@RestControllerAdvice` maps exceptions to JSON error bodies (RFC 7807 `ProblemDetail`) with the right status code.
- **Validation at the edge.** Bean Validation rejects bad input (empty seat list, more than 10 seats, blank name) before it reaches the service.
- **H2 in tests, PostgreSQL in the app.** Tests are fast and need no setup. The trade-off is that H2's SQL dialect differs slightly from PostgreSQL.

## Tests

- `BookingStatusTest`: the state machine rules.
- `BookingServiceTest`: seat holds, all-or-nothing booking, confirm, cancel, expiry with a controllable clock, idempotency.
- `BookingConcurrencyTest`: 50 threads start together. With one seat, exactly one request succeeds. With 5 seats and 50 requests, exactly 5 succeed and no seat belongs to two bookings.
- `BookingControllerTest`: HTTP layer with the service mocked using Mockito.
- `BookingExpiryJobTest`: the scheduled job, with Mockito.
- `BookingApiTest`: the full stack through MockMvc.

## Continuous integration and hosting

GitHub Actions (`.github/workflows/ci.yml`) runs `mvn -B verify`, which includes every test, on each push and pull request.

`render.yaml` is a Render Blueprint: one web service built from the `Dockerfile` and one PostgreSQL database. To deploy, push the repo to GitHub, then in Render choose **New > Blueprint**, select the repo and apply. Render passes the database details to the app as `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD`, which `application.yml` reads. The health check path is `/actuator/health`.

Free-tier notes, from Render's documentation at the time of writing: a free web service sleeps after 15 minutes without traffic and takes about a minute to wake up, and a free database expires 30 days after it is created.

`requests.http` holds ready-made requests for IntelliJ's built-in HTTP Client (where your edition includes it) or the REST Client extension for VS Code.

## Known limits

- The schema is created by Hibernate (`ddl-auto: update`). A real deployment would use Flyway or Liquibase migrations.
- A seat held by an expired booking is released when the job next runs, so it can stay unavailable for up to 30 seconds after expiry.
- Two identical requests arriving at exactly the same moment, with the same key, may both be attempted. One succeeds and the other gets a `409`; the client's next retry then returns the original booking.
- With several application instances the expiry job runs on each one. This is safe because of the optimistic lock, but a distributed lock (for example ShedLock) would avoid the duplicate work.
- Payment is simulated by `confirm`. There is no authentication.
