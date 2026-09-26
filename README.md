# NetPulse Enterprise

A distributed real-time network health and latency monitoring dashboard.

| Tier | Technology |
|---|---|
| Desktop client | JavaFX 21, Java 17+, `java.net.http.HttpClient` |
| REST backend | Javalin 5 (embedded Jetty), Jackson, HikariCP, plain JDBC |
| Database | PostgreSQL 13+ (MySQL 8 script also provided) |

---

## 1. Architecture and data flow

```
 ┌────────────────────────────────────── JavaFX Desktop Client ──────────────────────────────────────┐
 │                                                                                                   │
 │  FX Application Thread                    Background threads (all daemon)                         │
 │  ─────────────────────                    ──────────────────────────────                          │
 │  ┌──────────────────┐                     ┌───────────────────────────┐                           │
 │  │ DashboardControl │   register/start    │ ProbeScheduler            │                           │
 │  │  • TableView     │────────────────────▶│  ScheduledExecutorService │                           │
 │  │  • LineChart     │                     │  tick every 500 ms        │                           │
 │  │  • Add / Remove  │                     └────────────┬──────────────┘                           │
 │  └────────▲─────────┘                                  │ submit (skip if in-flight)               │
 │           │ Platform.runLater(result)                  ▼                                          │
 │           │                              ┌───────────────────────────┐   HTTP HEAD / TCP connect  │
 │           └──────────────────────────────│ Worker pool (8 threads)   │───────────────────────────▶│ ── targets
 │                                          │  NetworkProbe             │◀───────────────────────────│
 │                                          └────────────┬──────────────┘   latency / failure        │
 │                                                       │ offer()                                   │
 │                                          ┌────────────▼──────────────┐                            │
 │                                          │ LinkedBlockingQueue       │  bounded, 5 000 samples    │
 │                                          └────────────┬──────────────┘                            │
 │                                                       │ drain ≤200 every 3 s                      │
 │                                          ┌────────────▼──────────────┐                            │
 │                                          │ ApiClient (async HTTP)    │                            │
 │                                          └────────────┬──────────────┘                            │
 └───────────────────────────────────────────────────────┼────────────────────────────────────────────┘
                                                         │ JSON over HTTP
                                                         ▼
 ┌──────────────────────────────── REST Backend (Javalin, port 7070) ─────────────────────────────────┐
 │   Controllers  ──▶  Services (validation)  ──▶  DAOs (PreparedStatement)  ──▶  HikariCP pool       │
 │   EndpointController / MetricsController        EndpointDao / LatencyLogDao                        │
 └────────────────────────────────────────────────┬───────────────────────────────────────────────────┘
                                                  │ JDBC
                                                  ▼
 ┌──────────────────────────────────────── PostgreSQL ────────────────────────────────────────────────┐
 │  endpoints ──1:N──▶ latency_logs     index (endpoint_id, recorded_at DESC)                         │
 └────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Same flow as a Mermaid graph (renders on GitHub):

```mermaid
flowchart LR
    subgraph Client["JavaFX Desktop Client"]
        UI["DashboardController<br/>TableView + LineChart<br/><i>FX Application Thread</i>"]
        SCH["ProbeScheduler<br/>ScheduledExecutorService"]
        WRK["Worker pool<br/>NetworkProbe"]
        Q["LinkedBlockingQueue<br/>bounded 5000"]
        API["ApiClient<br/>java.net.http.HttpClient"]
    end
    TGT(["Monitored targets<br/>HTTP HEAD / TCP connect"])
    subgraph Backend["Javalin REST API :7070"]
        CTRL["Controllers"] --> SVC["Services<br/>validation"] --> DAO["DAOs<br/>PreparedStatement"]
    end
    DB[("PostgreSQL<br/>endpoints / latency_logs")]

    UI -->|start / register| SCH
    SCH -->|submit, skip if in-flight| WRK
    WRK <-->|probe| TGT
    WRK -->|offer ProbeResult| Q
    WRK -->|Platform.runLater| UI
    Q -->|drain batch every 3s| API
    API -->|POST /api/metrics/batch| CTRL
    API -->|GET /api/endpoints, history| CTRL
    DAO <--> DB
```

### Why it never freezes

| Risk | Mitigation |
|---|---|
| Blocking I/O on the UI thread | Probes run only on the worker pool; `ApiClient` is fully async (`CompletableFuture`) |
| Unsafe node mutation | Worker threads emit immutable `ProbeResult`; the controller applies them inside `Platform.runLater` |
| Task pile-up | Per-target `AtomicBoolean` in-flight guard + the rule `timeoutMs ≤ checkIntervalSec × 1000`, enforced server-side |
| Chart stutter | `setAnimated(false)` on chart and both axes, `setCreateSymbols(false)` |
| Memory bloat | Every series pruned to 20 points; upload queue bounded at 5 000 samples |
| Threads outliving the window | All pools use daemon threads; `stage.setOnCloseRequest` → `ProbeScheduler.shutdown()` with a final flush |
| Backend outage | Probing is decoupled from uploading — failed batches are re-queued, the dashboard keeps updating |

---

## 2. Project layout

```
netpulse-enterprise/
├── database/
│   ├── schema.sql              # PostgreSQL DDL + indexes + seed data
│   └── schema_mysql.sql        # MySQL 8 equivalent
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/netpulse/backend/
│       ├── BackendApplication.java      # entry point, DI wiring, error mapping
│       ├── config/     AppConfig, DatabaseManager
│       ├── model/      Endpoint, LatencyLog
│       ├── dto/        ApiResponse, EndpointRequest, MetricSample, MetricBatchRequest
│       ├── dao/        EndpointDao, LatencyLogDao
│       ├── service/    EndpointService, MetricsService
│       ├── controller/ EndpointController, MetricsController, ControllerSupport
│       └── exception/  ValidationException, NotFoundException, DataAccessException
└── frontend/
    ├── pom.xml
    └── src/main/java/com/netpulse/client/
        ├── MainApp.java
        ├── model/  EndpointFx (observable), EndpointDto, LatencyLogDto, MetricSampleDto, ...
        ├── net/    ApiClient, ApiException
        ├── probe/  NetworkProbe, ProbeScheduler, MonitoredTarget, ProbeResult
        └── ui/     DashboardController
```

---

## 3. Setup

### 3.1 Prerequisites

* JDK 17 or newer (`java -version`)
* Maven 3.8+ (`mvn -v`)
* PostgreSQL 13+ running locally

### 3.2 Initialize the database

```bash
# create role + database (run once, as a superuser)
sudo -u postgres psql -c "CREATE USER netpulse WITH PASSWORD 'netpulse';"
sudo -u postgres psql -c "CREATE DATABASE netpulse OWNER netpulse;"

# apply schema, indexes and seed data
psql "postgresql://netpulse:netpulse@localhost:5432/netpulse" -f database/schema.sql

# confirm
psql "postgresql://netpulse:netpulse@localhost:5432/netpulse" -c "\d+ latency_logs"
psql "postgresql://netpulse:netpulse@localhost:5432/netpulse" -c "SELECT COUNT(*) FROM endpoints;"
```

Windows PowerShell users can run the same `psql` commands from the PostgreSQL `bin` directory.

### 3.3 Run the backend

```bash
cd backend

# configuration (defaults shown; export only what you need to change)
export NETPULSE_DB_URL="jdbc:postgresql://localhost:5432/netpulse"
export NETPULSE_DB_USER="netpulse"
export NETPULSE_DB_PASSWORD="netpulse"
export NETPULSE_API_PORT=7070

mvn clean package          # produces target/netpulse-backend.jar (fat jar)
java -jar target/netpulse-backend.jar

# or, during development:
mvn exec:java
```

Expected output: `NetPulse backend listening on http://localhost:7070`.
If the database is unreachable the process logs the reason and exits with code 1 rather than serving broken routes.

### 3.4 Run the JavaFX client

```bash
cd frontend

export NETPULSE_API_URL="http://localhost:7070"   # optional, this is the default
mvn clean javafx:run
```

The `javafx-maven-plugin` downloads the platform-specific JavaFX modules and sets `--module-path` automatically, so no manual SDK install is needed.

---

## 4. REST API reference

All responses use one envelope:

```json
{ "success": true, "data": ..., "message": "..." }
```

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | `/api/endpoints` | 200 | 500 |
| POST | `/api/endpoints` | 201 | 400 (validation, duplicate name) |
| DELETE | `/api/endpoints/{id}` | 200 | 400 (non-numeric id), 404 |
| POST | `/api/metrics/batch` | 201 | 400 (empty/oversized batch, unknown endpoint id) |
| GET | `/api/metrics/{id}/history?limit=N` | 200 | 400, 404 |
| GET | `/api/health` | 200 | — |

Timestamps are ISO-8601 (`2026-09-20T11:42:13.512+06:00`). Field names are camelCase on the wire and snake_case in SQL.

---

## 5. Verification

### 5.1 Backend ↔ database

```bash
# health
curl -s localhost:7070/api/health

# list seeded endpoints
curl -s localhost:7070/api/endpoints

# create a target
curl -s -X POST localhost:7070/api/endpoints \
     -H 'Content-Type: application/json' \
     -d '{"name":"Example","targetAddress":"https://example.com","checkIntervalSec":5,"timeoutMs":1500}'

# validation must fail with HTTP 400 (timeout longer than the interval)
curl -s -i -X POST localhost:7070/api/endpoints \
     -H 'Content-Type: application/json' \
     -d '{"name":"Bad","targetAddress":"https://example.com","checkIntervalSec":1,"timeoutMs":9000}' | head -1

# unknown id must return 404
curl -s -i -X DELETE localhost:7070/api/endpoints/999999 | head -1

# ingest a batch by hand
curl -s -X POST localhost:7070/api/metrics/batch \
     -H 'Content-Type: application/json' \
     -d '{"samples":[{"endpointId":1,"latencyMs":42,"reachable":true,"statusMessage":"manual test"}]}'

# read it back
curl -s "localhost:7070/api/metrics/1/history?limit=5"
```

### 5.2 Client ↔ backend ↔ database

1. Start the backend, then `mvn javafx:run` in `frontend/`. The table fills with the seeded targets and the status bar reports how many loaded — that confirms `GET /api/endpoints`.
2. Press **Start Monitoring**. Within a second or two latencies appear, statuses flip to `ONLINE`/`SLOW`/`OFFLINE`, and the chart begins drawing. The seeded history is pre-plotted at negative x values.
3. Watch the status bar counters (`probes / uploaded / queued / dropped`). `uploaded` climbing every ~3 s confirms `POST /api/metrics/batch`.
4. Confirm rows are landing in SQL:

   ```bash
   psql "postgresql://netpulse:netpulse@localhost:5432/netpulse" \
        -c "SELECT endpoint_id, latency_ms, is_reachable, recorded_at
            FROM latency_logs ORDER BY recorded_at DESC LIMIT 10;"
   ```

5. Add a target through the form (e.g. `1.1.1.1:53`) — it should appear in the table, in the DB, and start being probed immediately.
6. Add a deliberately dead target (e.g. `203.0.113.7:8080`). Its status must go `OFFLINE` with `Timeout after 1200 ms` while every other row keeps updating — that demonstrates isolation of blocking probes.
7. **Kill the backend while monitoring runs.** The UI keeps probing and updating; the status bar shows a sync failure and `queued` grows. Restart the backend and the buffered samples are flushed on the next tick.
8. Select a row and press **Remove Selected**, then confirm the cascade:

   ```bash
   psql "postgresql://netpulse:netpulse@localhost:5432/netpulse" \
        -c "SELECT COUNT(*) FROM latency_logs WHERE endpoint_id = <removed id>;"   -- expect 0
   ```

9. Close the window. The JVM should exit immediately with no lingering `netpulse-probe-*` threads (`jcmd <pid> Thread.print` before closing shows them as daemons).

### 5.3 Index sanity check

```sql
EXPLAIN ANALYZE
SELECT * FROM latency_logs WHERE endpoint_id = 1 ORDER BY recorded_at DESC LIMIT 20;
-- expect: Index Scan Backward using idx_latency_logs_endpoint_recorded
```

---

## 6. Configuration reference

| Variable | Default | Applies to |
|---|---|---|
| `NETPULSE_DB_URL` | `jdbc:postgresql://localhost:5432/netpulse` | backend |
| `NETPULSE_DB_USER` | `netpulse` | backend |
| `NETPULSE_DB_PASSWORD` | `netpulse` | backend |
| `NETPULSE_DB_POOL_SIZE` | `10` | backend |
| `NETPULSE_API_PORT` | `7070` | backend |
| `NETPULSE_MAX_BATCH` | `500` | backend |
| `NETPULSE_MAX_HISTORY` | `500` | backend |
| `NETPULSE_API_URL` | `http://localhost:7070` | client |

To run against MySQL instead, apply `database/schema_mysql.sql` and set
`NETPULSE_DB_URL=jdbc:mysql://localhost:3306/netpulse?serverTimezone=UTC` — the driver is already in `pom.xml` and every DAO uses portable JDBC.

---

## 7. Tuning notes

* **Worker pool size** (`DashboardController.WORKER_THREADS`, default 8) should be roughly the number of targets you expect to probe concurrently. Probes block on sockets, so this pool is sized for latency, not CPU count.
* **Flush cadence** (`ProbeScheduler.FLUSH_SECONDS`, default 3 s) trades write amplification against how stale the DB may be. One batch insert of 200 rows costs about the same as one insert of one row.
* **Chart window** (`MAX_POINTS_PER_SERIES`, default 20) is the memory/history trade-off; the full history always lives in SQL and is reachable through `/api/metrics/{id}/history`.
* **Retention**: `latency_logs` grows at `targets × 3600 / interval` rows per hour. The purge statement at the bottom of `schema.sql` is the simplest remedy; partitioning by month is the next step up.
