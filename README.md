# Shoe Store API

Spring Boot backend of the Verdant Steps shoe store: catalog items, an anonymous cart that holds
stock, checkout, and payments through an external provider with retries.

- Java 25, Spring Boot 4.1, PostgreSQL 16, Flyway, OpenFeign
- The storefront (Next.js) lives in `~/WebstormProjects/storefront`, see [Run the storefront](#run-the-storefront)

## Requirements

| To run with | You need |
|---|---|
| Docker Compose (simplest) | Docker Desktop |
| Your IDE or a terminal | Docker (for Postgres) and JDK 25. Maven isn't needed, `./mvnw` downloads it |

## 1. Run everything with Docker Compose

Starts Postgres and the app, with the built-in fake payment provider.

```bash
docker compose up -d --build
```

- API: http://localhost:8080 (try http://localhost:8080/v1/items)
- Postgres: `localhost:5432`, database, user and password from [`environment/.env`](environment/.env)
- On first start Flyway creates the schema and seeds 133 catalog items (`V7__seed_items.sql`).

Rebuild after code changes with the same command. Other useful commands:

```bash
docker compose logs -f shoe-store
```
```bash
docker compose down
```
```bash
docker compose down -v
```

`down` keeps the data, `down -v` also deletes the database volume (the next start seeds it again).

## 2. Run the app from your IDE or a terminal

Postgres still runs in Docker:

```bash
docker compose up -d postgres
```

The app takes the database name and credentials from the same variables Postgres uses, so load
`environment/.env` into your shell and start the app with the `local` profile:

```bash
set -a; source environment/.env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

If the Docker app is still running on 8080, stop it first (`docker compose stop shoe-store`) or
add `-Dspring-boot.run.arguments=--server.port=8081`.

**IntelliJ IDEA:** run `ComApplication` with

- Active profiles: `local`
- Environment variables: the three lines of `environment/.env`
  (`POSTGRES_DB=...;POSTGRES_USER=...;POSTGRES_PASSWORD=...`)

**As a jar:**

```bash
./mvnw -DskipTests package
java -jar target/shoe-store-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

## Configuration

Set as environment variables (or `--spring...` arguments).

| Variable | Default | What it is |
|---|---|---|
| `DB_HOST` | `localhost` | Postgres host (Compose sets `postgres`) |
| `POSTGRES_DB` | `shoe_store` | Database name |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `postgres` / `postgres` | Database credentials |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | – | Override the three above with a full JDBC URL and credentials |
| `SPRING_PROFILES_ACTIVE` | – | `local` = use the built-in fake payment provider |
| `PAYMENT_PROVIDER_URL` | – | Base URL of a real payment provider. **The app won't start without this or the `local` profile** |

Cart holds, payment retries and timeouts are tuned under `shop.cart` and `shop.payment` in
[`application.yaml`](src/main/resources/application.yaml). The app always runs in UTC.

## Try the API

```bash
curl -s 'localhost:8080/v1/items?size=3'
```

A whole purchase from the terminal (the cart lives in the `cart_id` cookie):

```bash
ITEM=$(curl -s 'localhost:8080/v1/items?size=1' | sed -E 's/.*"id":"([^"]+)".*/\1/')
curl -s -c cart.txt -X POST localhost:8080/v1/cart/lines -H 'Content-Type: application/json' -d "{\"itemId\":\"$ITEM\",\"quantity\":1}"
ORDER=$(curl -s -b cart.txt -X POST localhost:8080/v1/cart/checkout -H 'Content-Type: application/json' -d '{"paymentType":"VISA"}' | sed -E 's/.*"id":"([^"]+)".*/\1/')
curl -s -X POST localhost:8080/v1/orders/$ORDER/payments -H 'Content-Type: application/json' -d '{"paymentType":"VISA"}'
```

### Endpoints

| | |
|---|---|
| `GET/POST /v1/items`, `GET/PUT/DELETE /v1/items/{id}` | Catalog (one item = model + colour + size; prices in cents) |
| `GET /v1/cart` | The cart, renews its holds |
| `POST /v1/cart/lines` / `PUT /v1/cart/lines` | Add pairs (holds stock) / give pairs back |
| `DELETE /v1/cart/lines/{itemId}` | Remove a line |
| `POST /v1/cart/heartbeat` | Keeps the holds alive while the page is open |
| `POST /v1/cart/checkout` | Cart becomes a placed order, held pairs are sold |
| `GET/POST /v1/orders`, `GET/DELETE /v1/orders/{id}` | Orders (carts aren't listed) |
| `POST /v1/orders/{id}/payments` | Pay: 201 paid, 202 retried in the background, 402 declined, 409 can't be paid |
| `GET /v1/payments/{id}/status` | Asks the provider and syncs the payment |

### The fake payment provider (`local` profile only)

It answers under `/mock-provider`. Script what the next charges do (`OK`, `DECLINE`, `ERROR`,
`TIMEOUT`, `CHARGE_THEN_TIMEOUT`) to see declines and retries:

```bash
curl -X POST localhost:8080/mock-provider/admin/script -H 'Content-Type: application/json' -d '{"outcomes":["DECLINE","OK"]}'
```
```bash
curl localhost:8080/mock-provider/admin/stats
```

## Run the storefront

In `~/WebstormProjects/storefront` (Node 20.9 or newer), with the API running on 8080:

```bash
npm install
cp .env.example .env.local
npm run dev
```

Open http://localhost:3000. The storefront calls `/shop-api/...` on itself and Next.js forwards it
to the API, so the cart cookie works without CORS. If the API isn't on `localhost:8080`, set
`SHOP_BACKEND_URL` in `.env.local` and restart `npm run dev`.

## Tests

`ComApplicationTests` starts the whole app, so it needs Postgres running and the variables from
`environment/.env` loaded:

```bash
set -a; source environment/.env; set +a
SPRING_PROFILES_ACTIVE=local ./mvnw test
```

The profile goes in as an environment variable: `-Dspring.profiles.active` would only reach Maven,
not the JVM the tests run in.

## Troubleshooting

- **`shop.payment.base-url (PAYMENT_PROVIDER_URL) is not set`**: start with the `local` profile or set `PAYMENT_PROVIDER_URL`.
- **`port is already allocated` / app can't bind 8080**: another Postgres or app is running; stop it or change the port mapping in `docker-compose.yaml`.
- **`FATAL: password authentication failed`**: the app isn't getting the credentials from `environment/.env`; load them as shown above.
- **DBeaver: `invalid value for parameter "TimeZone": "Europe/Kiev"`**: add `-Duser.timezone=Europe/Kyiv` as the last line of `/Applications/DBeaver.app/Contents/Eclipse/dbeaver.ini` and restart DBeaver.
