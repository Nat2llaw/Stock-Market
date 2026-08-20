# Stock Market Monitoring Application

Retrieves Apple (AAPL) stock data from a public API, stores it in PostgreSQL, and presents it
through a REST API and a React web application.

Java / Spring Boot backend, React + TypeScript frontend.

---

## Repository layout

```
Backend/              Spring Boot service — retrieval, storage, REST API
Frontend/             React + TypeScript UI, and its nginx config for production
docker-compose.yml    PostgreSQL + backend + frontend, one command
```

---

## Architecture

### High-level

```
                                    React + TypeScript UI
                                            │  REST / JSON
                        ┌───────────────────┴───────────────────┐
                        ▼                                       ▼
              GET /api/stocks/…                     POST /api/stocks/…/refresh
                        │                                       │
                        │                            StockRetrievalService
                        │                                       │  Yahoo v8 chart API
                        │                                       ▼
                        └──────────► PostgreSQL ◄──── StockStorageService
```

**The GET endpoints read the database, never the upstream.** Only `POST /refresh` talks to
Yahoo. This means user-facing latency does not depend on a third party, request volume against
an unofficial API is bounded by explicit user action rather than by page traffic, and the site
keeps answering during an outage — with data that is stale and labelled as such, because every
response carries the time it was retrieved.

The trade is that the database starts empty. Until a refresh runs, reads return `404` with
type `urn:stocks:no-stored-data`, which the UI shows as a "Refresh now" prompt rather than as
a failure — the distinction that error type exists for.

### Technology choices

| Choice | Why |
|---|---|
| **Yahoo v8 chart endpoint**, called directly | It answers unauthenticated and returns the current price *and* the historical bars in one response. The obvious alternative, the `com.yahoofinance-api` library, is unmaintained and calls routes that now return **401** without a browser crumb — checked with `curl` before committing to this one, not assumed. |
| **Spring Boot 4.1 / Java 25** | Already the project's baseline. Spring Framework 7 ships `RetryTemplate` in `spring-core`, so retrying needed no extra dependency. |
| **PostgreSQL + Flyway** | The schema is versioned SQL that runs identically on a laptop, in tests and in a deployment. Hibernate runs at `ddl-auto=validate`, so the migrations own the schema and the mapping is checked against them at boot. |
| **`numeric(19,4)` for money, serialised as a string** | Binary floating point cannot represent decimal fractions exactly, and these values get summed and compared. A JSON number is a float64 in every mainstream client, so prices cross the wire as exact decimal strings and the client converts at its own boundary. |
| **Zonky embedded PostgreSQL for tests** | Real PostgreSQL binaries from a Maven artifact — no Docker daemon required. |
| **Records for domain and DTOs** | The data is immutable values. No Lombok is needed, so it was removed. |
| **Hand-drawn SVG chart** | One line over one series. A plotting library would be more code to audit and update than the arithmetic it replaces. |
| **No router, store or data-fetching library on the client** | One resource, one screen. `useState` in `App.tsx` is the whole store. The single piece of context is the theme, because the toggle that changes it sits several levels away from the components that read the palette. |

### Design decisions

**A `StockDataProvider` interface, not a concrete Yahoo class.** The upstream is the least
stable part of the system; Yahoo's unofficial endpoints have broken before and will again.
Everything above the seam depends on the contract.

**Retrieval failures are thrown, not swallowed.** Every retrieval is one a user asked for, so
somebody is waiting and deserves an error rather than a plausible-looking empty result. The
web layer turns the exception into a `503`; nothing in the service package mentions HTTP.

**A permanent rejection is never retried.** A delisted ticker is just as unknown on the third
attempt, and a malformed interval is just as malformed. Only a genuine outage — a timeout, a 5xx,
or a 429, where the request was fine and there were merely too many of them — is worth backing off
for. Everything else fails on the first attempt and comes back as a `400` naming the caller's
error rather than a `503` blaming a provider that is not down. The retry policy allow-lists the
retryable exception type, so this follows from the type system rather than from a string check.
Retryability is declared in exactly one place — the policy in `StockRetrievalConfig` — rather than
also being carried on the exceptions themselves, so a new failure type is terminal until it is
listed there.

**Quotes are appended; bars are upserted.** These record different things. Two refreshes a
minute apart are two *observations* of the price, but they must not be two copies of the same
trading *session*. A unique constraint on `(symbol, bar_interval, bar_timestamp)` makes
repeated refreshing idempotent, and the upsert refreshes rather than ignores because the
current session's bar keeps moving until the market shuts.

**The previous close is derived from the bars, not read from a field that looks like it.**
Yahoo's `chartPreviousClose` is the close before the requested *range* starts, not before the
current session — a month stale at the default `range=1mo`, and a different number for every
range. Taken at face value it reports a month's movement as a day's, which inverts the arrow
whenever the two disagree. Since the final bar is always the session the current price belongs
to, the previous close is the bar before it, independently of range. Null when there is nothing
honest to compare against — fewer than two bars, or an intraday interval — and both the API and
the UI treat a null as "no change to show" rather than as a zero move.

**A session's date belongs to the exchange, not to the reader.** A daily bar is stamped with the
instant its session opened — `13:30Z` for a US equity — which is not a date. Formatted in the
reader's own timezone that instant is the 17th in New York and the 18th in Auckland, so a reader
at UTC+11 or further east sees every row of the history table dated a day late. Yahoo names the
exchange's zone, so it is stored on the quote and sent to the client, which formats session dates
in it. `formatDate` takes the zone as a required argument rather than an optional one: the
regression is only possible if a caller can leave it out, so the type system does not let them.
Falls back to UTC, not to the reader, when the zone is unknown.

**Errors distinguish "no such symbol" from "not collected yet."** A fresh database has nothing
stored for a perfectly valid ticker, and telling a user "no such symbol" would be wrong. The
API returns different problem types, and the UI offers a refresh for the second.

**Input is checked against the column it lands in.** `symbol` is the only path parameter, and
every endpoint constrains it to `varchar(16)`'s width and requires it to look like a ticker —
letters, digits, and the punctuation real symbols use (`BRK-B`, `BRK.B`, `^GSPC`, `BTC-USD`,
`ES=F`). Anything else is a `400` before the upstream is called or a row is written, rather than
a database constraint surfacing later as a `500`. `interval` is length-checked on `POST /refresh`,
which is the only endpoint that writes it into `bar_interval varchar(8)`; on the `GET`s it is
just a query predicate, where an unstorable value can only fail to match and return no bars.

**The browser only ever makes same-origin requests.** The client calls the relative base `/api`,
never an absolute host: Vite proxies it in development and nginx proxies it in production, so
CORS does not arise in either environment. The configurable `app.cors.allowed-origins` covers
the remaining case — a dev server pointed at a separately hosted backend.

**Theme is one attribute, not a re-render.** The palette is CSS custom properties keyed off
`data-theme` on `<html>`, so flipping it repaints without React doing anything. An explicit
choice is stored in `localStorage`; until one is made the OS preference wins and keeps winning
via a `matchMedia` listener. A small inline script in `index.html` applies the same rule before
React mounts, so there is no flash of the wrong palette.

### Trade-offs

- **Refresh-on-demand rather than background polling** keeps the moving parts to a minimum and
  means the application never calls a third party unattended, but the data is only as current
  as the last time somebody asked, and a first-time visitor sees an empty state. A scheduled
  poller would fix both, at the cost of a timer, its configuration, and its failure handling.
- **Storing every observation forever** gives a complete audit trail and unbounded growth. It
  needs a retention policy before running anywhere for long.
- **Zonky rather than Testcontainers** buys a suite that runs without Docker, at the cost of a
  non-standard test dependency and a PostgreSQL version (14) that trails the deployed one (16).
- **Native SQL for the upsert** ties the storage layer to PostgreSQL. That is a deliberate
  commitment: the alternative is an extra round trip per bar and a race between concurrent
  refreshes.
- **No caching layer.** Reads hit PostgreSQL directly. At this scale that is the right amount
  of infrastructure; it would not survive real traffic.

---

## Running the Application

### With Docker (nothing else to install)

```bash
docker compose up --build
```

- UI: <http://localhost:5173>
- API: <http://localhost:8080/api/stocks/AAPL>

The backend image is a multi-stage build — JDK to compile, JRE to run — and drops to a non-root
user. The frontend image builds the Vite bundle and serves it from nginx, which also proxies
`/api` to the backend. `index.html` is served `no-cache` while the content-hashed bundles under
`/assets/` are cached for a year: a new build produces new filenames, so a rebuild takes effect
immediately instead of stranding a browser on assets that no longer exist.

### Without Docker

**Backend** — needs Java 25 and a PostgreSQL reachable at `localhost:5432`:

```bash
createdb stock_market                     # database "stock_market", user/password postgres
cd Backend
./mvnw spring-boot:run
```

Flyway creates the schema on first start. The database begins empty; the first **Refresh now**
in the UI — or `curl -X POST localhost:8080/api/stocks/AAPL/refresh` — fills it.

**Frontend** — needs Node 20+:

```bash
cd Frontend
npm install
npm run dev                               # http://localhost:5173, proxies /api to :8080
```

### Tests

```bash
# Backend
cd Backend
./mvnw test          # 25 tests. No network, no Docker — starts its own PostgreSQL.
./mvnw test -Plive   # only the 1 live test, against the real Yahoo API.

# Frontend
cd Frontend
npm test                  # 27 tests in jsdom, no backend required
npm run test:unit         # one module at a time, every collaborator stubbed
npm run test:integration  # the whole screen, only the network boundary stubbed
```

The frontend suite lives in [Frontend/tests/](Frontend/tests/), split by how much of the app is
real. `unit/` exercises one module against hand-built input, so a failure names the module.
`integration/` stubs only `fetch`, so it catches what lives *between* modules — a field the
client stops passing through, an error mapped to the wrong banner, a symbol that reaches the URL
untrimmed.

### Configuration

Everything below is set in `Backend/src/main/resources/application.properties` and can
be overridden by the usual Spring environment variables (`SPRING_DATASOURCE_URL`, and so on),
which is exactly what `docker-compose.yml` does.

| Property | Default | Meaning |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/stock_market` | Override with `SPRING_DATASOURCE_URL` |
| `app.yahoo-finance.default-symbol` | `AAPL` | The ticker this deployment monitors |
| `app.yahoo-finance.default-range` | `1mo` | How much history each refresh fetches |
| `app.yahoo-finance.default-interval` | `1d` | Bar width |
| `app.yahoo-finance.connect-timeout` | `5s` | |
| `app.yahoo-finance.read-timeout` | `10s` | |
| `app.yahoo-finance.max-retries` | `3` | Retries after the first attempt |
| `app.yahoo-finance.retry-delay` | `500ms` | Initial backoff |
| `app.yahoo-finance.retry-multiplier` | `2.0` | Backoff growth |
| `app.yahoo-finance.max-retry-delay` | `5s` | Backoff ceiling |
| `app.cors.allowed-origins` | Vite's ports | Clear it when the UI is served from the same origin |

These bind to a `@Validated` record, so a blank base URL or a negative retry count fails at
startup rather than at the first request.

### API

| Method | Path | Notes |
|---|---|---|
| GET | `/api/stocks/default` | The monitored ticker |
| GET | `/api/stocks/{symbol}?interval=` | Quote + history + retrieval time; `interval` defaults to `1d` |
| GET | `/api/stocks/{symbol}/quote` | Latest stored quote |
| GET | `/api/stocks/{symbol}/history?interval=&from=&to=` | Stored bars, newest first |
| POST | `/api/stocks/{symbol}/refresh?range=&interval=` | Retrieve now, store, return |

Errors are RFC 9457 problem details: **400** when the provider rejects the request itself — an
unsupported `range` or `interval` — **404** for an unknown symbol, **404** with type
`urn:stocks:no-stored-data` when the ticker is valid but nothing has been retrieved yet, **503**
when the provider cannot be reached. Framework-level rejections take the same shape, so a client
has one error contract to parse rather than two.

The UI uses three of the five. The overview endpoint carries the quote and the history together,
so a page load is one round trip and cannot show a quote and a history that straddle a refresh;
`POST /refresh` returns the same shape, so a successful refresh needs no follow-up request.

---

## Engineering Considerations

Short answers to the three topics the brief names. Each one links to the fuller version rather
than repeating it.

### Testing

**What is implemented** — 25 backend tests across six classes and 27 frontend tests across nine
files, all of which run with no network and no Docker. Each backend class covers one component,
with the composed system covered by the integration tests below. The commands are under [Tests](#tests).

- **Provider** (`YahooFinanceStockDataProviderTest`) — recorded Yahoo payloads replayed through
  `MockRestServiceServer`: the happy path, payloads missing OHLCV columns or closes, the
  200-with-an-error-body case, and each HTTP status mapped to the failure type it should become.
  Also that the previous close is the prior *session* rather than Yahoo's range-dependent
  `chartPreviousClose`, and is null when there is no session to compare against.
- **Retry** (`StockRetrievalServiceTest`) — counts provider calls to prove that an outage is
  retried and that an unknown symbol or a rejected request is not.
- **Web** (`StockControllerTest`, `StockApiIntegrationTest`) — the endpoint contracts, the two
  distinct `404`s, a `503` that does not leak the upstream's message, inputs rejected before any
  collaborator is touched, an open-ended history window closing at the injected clock, and money
  serialised as an exact decimal string at the scale the column stores.
- **Storage** (`StockStorageServiceIntegrationTest`) — runs against a real PostgreSQL started by
  Zonky, so the upsert, the unique constraint and `numeric` precision are exercised on the engine
  the application deploys against rather than on a stand-in.
- **Wiring** (`StockRetrievalWiringIntegrationTest`) — the shipped configuration binds and the
  beans compose, so a broken property name fails a test rather than a deployment.
- **Live** (`YahooFinanceLiveIntegrationTest`) — one opt-in test against the real endpoint,
  excluded from the default build so the build never fails because a third party is down. It
  also pins the upstream property the previous close is derived from — that the last bar is the
  session the current price belongs to — because a fixture can only ever prove that assumption
  held on the day it was recorded.
- **Frontend** — split into `unit/` (one module, every collaborator stubbed) and `integration/`
  (the whole screen, only `fetch` stubbed), so a failure says whether a module broke or the seam
  between two did.

**What I would add with more time**

- CI running both suites, with the `live` test nightly rather than on every commit.
- An end-to-end browser test — the one check still done by hand.
- Two concurrent refreshes against the real PostgreSQL, proving the idempotency the native
  upsert is justified by instead of asserting it in prose.
- `WebCorsConfig`, including its empty-origins branch, which no test covers.
- The response shape for an unexpected exception, which currently falls through to Boot's
  default handling rather than to the problem-detail contract.
- `from > to` on the history endpoint, which returns an empty list where a `400` would be honest.

### Error Handling

**When the stock API is unavailable.** `RestClient` is configured with a 5s connect and a 10s
read timeout, so a hung upstream cannot hold a thread open indefinitely. A timeout, a 5xx or a
429 becomes `StockDataUnavailableException`, which the retry policy allow-lists: up to three
retries with exponential backoff (0.5s, 1s, 2s, capped at 5s). A `404`, or a `4xx` that means
the request itself was wrong, becomes a terminal type and is not retried, because the second
attempt would be identical to the first.

If every attempt fails, the caller gets a `503` problem detail with a `retryAfterSeconds` hint
and a message that names no internal detail — the upstream's own text is logged, never returned.
Nothing is written, so the stored data is never left half-updated. Only `POST /refresh` touches
Yahoo, so an outage does not take the site down: every `GET` keeps serving what is stored, and
the UI keeps the last price on screen and adds a banner saying the refresh failed and that it is
showing the last data stored. Every response carries `retrievedAt`, so stale data is visibly
stale rather than silently so.

The bound worth stating: four attempts against a total blackhole take about 43 seconds before
that `503` arrives, and the browser's `fetch` has no timeout of its own. An overall deadline
across the retry chain plus a client-side abort — the cheap half of a circuit breaker — is the
first thing to add here.

**Validation considered.** `symbol` is constrained at every endpoint to the width of the column
it lands in and has to look like a ticker, and `interval` is length-checked on the one endpoint
that stores it, so a value that could not be stored is rejected with a `400` before Yahoo is
called rather than failing at the database afterwards. Symbols are normalised to uppercase, so `aapl` and `AAPL` are one resource
rather than two rows. Configuration binds to a `@Validated` record, so a blank base URL or a
negative retry count fails at startup rather than at the first request. The upstream payload is
treated as untrusted: an error block is checked even on an HTTP `200`, bars with a null close are
dropped, and OHLCV arrays shorter than the timestamp array are tolerated, so partial data
degrades to less history instead of failing the whole quote.

### Architecture

**Key design decisions** — a `StockDataProvider` seam so nothing above it mentions Yahoo;
retrieval failures thrown rather than swallowed, and turned into HTTP only at the web layer;
retryability decided by exception type in one place; quotes appended as observations while bars
are upserted per session, which makes refreshing idempotent; two different `404`s, because "no
such symbol" and "nothing collected yet" are different facts; and `GET`s that read only the
database, so user-facing latency never depends on a third party. The reasoning for each is in
[Design decisions](#design-decisions).

**Trade-offs** — refresh-on-demand over background polling, unbounded observation history over a
retention policy, Zonky over Testcontainers, PostgreSQL-specific SQL for the upsert, and no
caching layer. What each one buys and costs is in [Trade-offs](#trade-offs).

**How I would evolve it in production** — a market-hours-aware scheduled poller so the data is
current without a user asking; an overall deadline and a circuit breaker around the upstream,
with the client aborting its own request; a retention policy for `stock_quote` and a paged
history endpoint before either grows past a month of daily bars; authentication and rate limiting
in front of `POST /refresh`, which calls a third party on demand; credentials from a secrets
manager rather than the values checked into `docker-compose.yml` for local use; CI/CD running
both suites on every commit; and metrics and traces around retrieval — failure rate, retry count,
upstream latency — because the first sign that an unofficial endpoint has changed should be a
dashboard, not a user. Fuller list: [With More Time](#with-more-time).

---

## What's Complete

- **Data retrieval** — Yahoo v8 chart endpoint, timeouts, bounded exponential-backoff retries,
  a provider interface, and failure types that distinguish retryable from not.
- **Data storage** — PostgreSQL, Flyway-versioned schema, append-only quote observations,
  idempotent upserted history bars.
- **REST API** — five endpoints, DTO responses, RFC 9457 error handling.
- **Tests** — 25 backend and 27 frontend, all passing without network or Docker, plus 1 opt-in
  live one against the real Yahoo API. The frontend suite is split into unit and integration
  layers, the latter protecting the mapping from API payload to rendered element.
- **Frontend** — current price, change against previous close, SVG price chart, a paginated
  history table (ten bars a page, with the size of the whole set in its heading), retrieval
  timestamps throughout, and a light/dark theme that follows the operating system until the
  user overrides it.
- **Deployment** — Docker Compose, backend and frontend Dockerfiles, nginx config with cache
  headers matched to the build's content hashing; run end to end against live Yahoo data.
- **Documentation** — this README: the architecture and the reasoning behind it, the API
  contract, the data model, and the setup instructions for both ways of running it.

## What's Missing

- **No CI pipeline.** Both suites run locally; nothing runs them automatically.
- **No end-to-end browser test.** The UI has been rendered against the live backend by hand,
  but that check is not automated.
- **No authentication or rate limiting**, including on `POST /refresh`.
- **No load or performance testing** of any kind.
- **The frontend suite runs only under `TZ=UTC`.** Pinning it makes the suite deterministic, but
  it also means a timezone bug cannot fail it. Session dates are now guarded by `formatDate`
  requiring an explicit zone — a call site cannot silently fall back to the reader's — so the
  type system covers the regression the second run would have caught. A hostile-zone run
  (`TZ=Pacific/Auckland`) would still be worth adding, and needs `@types/node` to read the
  ambient variable from `vite.config.ts`.

## Known Limitations

- Data is only as current as the last refresh. Nothing updates it in the background, so a
  freshly started instance shows an empty state until somebody presses **Refresh now**.
- `stock_quote` grows by one row per refresh forever; there is no retention policy.
- **A quote's `previous_close` is a property of the interval it was retrieved at, but
  `stock_quote` is keyed only by symbol.** Refreshing at an intraday interval
  (`?interval=1h`) appends a quote with a null previous close — correct in itself, since an
  hourly bar is not a session — and because that row is then the latest quote for the symbol,
  the daily view shows no change indicator until the next daily refresh. The shipped UI only
  ever refreshes at the default `1d`, so this needs a hand-made API call to provoke. The real
  fix is to stop storing `previous_close` on the observation and derive it at read time from
  the bars of the interval being viewed, which is a change to the storage seam rather than to
  the provider.
- The API has no authentication or rate limiting, including on `POST /refresh`, which calls a
  third party on demand.
- Yahoo's endpoints are unofficial and can change without notice. The `live`-tagged tests are
  the early warning; they are excluded from the default build on purpose.
- History is returned unpaginated. The table now pages through it ten bars at a time, so the
  DOM stays small, but the client still fetches and holds every stored bar for the symbol —
  fine for a month of daily bars, not for years of them. The fix is a paged endpoint, not more
  client-side slicing.
- Compose credentials are `postgres`/`postgres`, checked into the file — correct for a local
  development stack, and exactly what must not ship to a deployed environment.

## With More Time

1. CI running `./mvnw test` plus the frontend build and suite, with the `live` tests on a
   nightly schedule so upstream breakage is noticed without making every commit depend on Yahoo.
2. Rate limiting on `POST /refresh`, and authentication in front of the API.
3. A retention policy for observations, and pagination on the history endpoint. The table
   already pages client-side; making the pager fetch a page at a time is what stops the whole
   series crossing the wire.
4. An automated end-to-end browser test, the one check still done by hand.
5. Background refresh, if the product wants data that is current without a user asking — best
   done market-hours-aware rather than on a blind fixed interval.
6. A range selector on the chart, wired to the `history?from=&to=` endpoint that already exists
   and is currently unused by the UI.

---
