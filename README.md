# CityRide

CityRide is a full-stack cab-booking application built with Java 25, Spring Boot,
PostgreSQL, and React. The application supports rider, driver, and operations
workflows, with third-party integrations configured through environment variables.

## Features

- Rider and driver registration, BCrypt password hashing, JWT authorization, and
  optional verified Google OAuth 2.0 sign-in.
- Address search, OpenStreetMap maps, OSRM driving routes, route distance, and
  estimated driving time. Routes use real road-network data; travel-time estimates
  do not include live traffic.
- Fare estimates and server-calculated fares (INR 50 base + INR 15/km, with
  a INR 100 minimum before applicable promotions).
- Rider ride requests, history, safe cancellations, fare discounts, and driver
  matching for online drivers within 25 km when both parties share coordinates.
- Driver request queues with accept/decline, online/offline availability,
  browser-geolocation updates, earnings, ratings, and leaderboards.
- Authenticated STOMP/WebSocket updates for ride events, live driver positions,
  available requests, and operational SOS alerts.
- Stripe Checkout for card payments and signed, amount-verified webhooks. The
  application never collects or stores payment-card numbers.
- Private PDF receipts, optional SMTP receipt delivery, one-time trip reviews,
  customer support tickets, and operations tools for tickets and promotions.
- PostgreSQL persistence, Docker Compose, Nginx, and GitHub Actions CI.

## Local development

Requirements: Java 25+, Maven 3.9+, Node.js 20+, and npm.

Start the API in one terminal:

```sh
cd backend
mvn spring-boot:run
```

In a second terminal, start the web app:

```sh
cd frontend
npm ci
npm run dev
```

Open <http://localhost:5173>. Local development defaults to an H2 file database
in `backend/data`. The default, non-production Spring profile creates demo
rider, driver, and admin accounts:

| Role | Email | Password |
| --- | --- | --- |
| Rider | rider@cityride.com | RideDemo2026! |
| Driver | driver@cityride.com | RideDemo2026! |
| Admin | admin@cityride.com | RideDemo2026! |

Demo credentials are for local development only. The `production` profile disables
demo-account and sample-promotion seeding.

## Map and routing

The frontend uses Leaflet/OpenStreetMap centered on India and the public Nominatim
and OSRM demonstration services. Address search is restricted to India. No Google
Maps API key is required. Geocoding and routing require Internet access. Public demonstration endpoints have fair-use limits and
are not an uptime or commercial-service guarantee. Before a high-traffic
production deployment, configure contracted geocoding and routing services and
update `frontend/src/maps.js`. Users must permit browser location access to send
the driver's live position; the app does not simulate vehicle location.

## Optional provider integrations

### Google sign-in

1. Create OAuth 2.0 credentials in Google Cloud for a Web application.
2. Register this authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`.
3. Start the backend with `GOOGLE_OAUTH_ENABLED=true`,
   `GOOGLE_CLIENT_ID=...`, and `GOOGLE_CLIENT_SECRET=...`.
4. Add `VITE_GOOGLE_OAUTH_ENABLED=true` to `frontend/.env.local`, then restart
   Vite. The login page shows the Google sign-in button.

Google sign-in creates rider accounts only; it cannot create or link admin
accounts. The standard verified-email password login remains available.

### Stripe test payments

Set `STRIPE_SECRET_KEY` to an `sk_test_...` key and
`STRIPE_WEBHOOK_SECRET` to the signing secret for the Stripe webhook. Configure
Stripe Checkout to send `checkout.session.completed` events to
`http://localhost:8080/api/payments/webhook`. With the Stripe CLI installed, run:

```sh
stripe listen --forward-to localhost:8080/api/payments/webhook
```

Checkout charges are created in INR; confirm your Stripe account is enabled to
accept INR payments. Use Stripe test cards and test keys only for testing. A checkout return is **not**
treated as proof of payment: the application marks an invoice paid only after a
valid, signed webhook matches the recorded checkout session, rider, currency,
and fare. Stripe is not enabled without credentials.

### PDF email receipts

Configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and
`MAIL_FROM`. When SMTP is not configured, downloading PDF receipts still works
and the email action reports an explicit configuration error.

## PostgreSQL with Docker Compose

Start by copying the example environment file and changing every sample secret:

```sh
cp .env.example .env
```

Set a strong, randomly generated `JWT_SECRET` and `POSTGRES_PASSWORD`. Set
`INITIAL_ADMIN_EMAIL` and `INITIAL_ADMIN_PASSWORD` to provision your first
production administrator. Then build and start PostgreSQL, the API, and the
Nginx-served frontend:

```sh
docker compose up --build
```

Open <http://localhost:8080>. The database uses a persistent Docker volume.
Production startup fails clearly if there is no existing administrator and no
valid initial admin credentials. Leave the optional OAuth, Stripe, and SMTP
settings empty to keep those provider integrations unavailable.

Never commit `.env` or production credentials. Before deploying publicly,
configure HTTPS at the ingress/load balancer, use managed secrets and a managed
PostgreSQL service, set `FRONTEND_URL` to the HTTPS application origin, and
register `${FRONTEND_URL}/login/oauth2/code/google` with Google if OAuth is
enabled.

## Automated checks

```sh
export JAVA_HOME=/path/to/jdk-25
mvn -f backend/pom.xml clean verify
npm --prefix frontend ci
npm --prefix frontend run build
docker compose config --quiet
```

GitHub Actions runs the Java 25 backend tests, frontend production build, and
Docker Compose configuration validation on pushes to `main`/`master` and on
pull requests.

## API outline

The authenticated REST API includes:

- `/api/auth/register`, `/api/auth/login`, and `/api/auth/me`
- `/api/rides`, `/api/rides/estimate`, `/api/rides/mine`, and
  `/api/rides/available`
- `/api/rides/{id}/accept`, `/decline`, `/status`, `/pay`, `/rating`, `/sos`,
  `/receipt`, and `/receipt/email`
- `/api/rides/driver/availability` and `/api/drivers/rankings`
- `/api/support/tickets` and `/api/support/tickets/mine`
- Admin-only `/api/admin/**`, `/api/admin/promo-codes/**`, and
  `/api/support/tickets/admin/**`
- Stripe-signed webhook endpoint `/api/payments/webhook`
- JWT-authenticated STOMP endpoint `/ws`; subscribe to
  `/topic/drivers/requests`, `/topic/admin/sos`, and
  `/user/queue/ride-events`

All new fares and payments use INR. Existing rides already stored in the local
database retain their original numeric fare values; they are not automatically
converted from the previous USD pricing.

The optional Redis and Kafka components in the requirements document are not
needed for this single-instance application; the built-in WebSocket broker is
in-memory and is not a horizontally scaled message broker.
