# OpenRune Central Server

Kotlin/Ktor service for accounts, world-link auth, sessions, activity logs, and HTTP APIs used by OpenRune game worlds.

## Configuration

Central loads **`central-config.properties`** (Java properties) from **one** of these places, in order:

1. **`OPENRUNE_CONFIG`** — absolute or relative path to a file (if it exists and is readable).
2. **`./central-config.properties`** — next to the process current working directory.
3. **`central-config.properties`** — next to the running JAR (when Central is started from a `.jar`).

If none of those files exist, built-in defaults apply (see `CentralRuntimeConfig.kt`). For each setting, **environment variables override** both file values and defaults.

Placeholders in the file are supported, e.g. `{env.MY_PASSWORD}`.

Optional HTTP port: **`OPENRUNE_HTTP_PORT`** or **`openrune.http.port`** before startup (otherwise see `openrune-central/src/main/resources/application.yaml`).

### Same Postgres vs its own database (examples)

**Separate database (typical production)** — Central has its own Postgres (host/user/db), game servers use their own game DB plus whatever they need to talk to Central:

`central-config.properties` on the Central host:

```properties
openrune.jdbc.url=jdbc:postgresql://central-db.internal:5432/openrune_central
openrune.db.user=openrune
openrune.db.password=your-secret
openrune.worldsLinkPort=9091
```

Game server: point world-link / Central auth at this Central host; set the game’s **Central JDBC URL** to the same JDBC URL (or another user with access to the same DB) if the game writes activity logs into Central’s Postgres.

**Shared Postgres (“same instance”, typical local dev)** — one PostgreSQL process, one logical database, both processes use the **same JDBC URL** (often `localhost` and the same database name your embedded / dev stack already uses):

`central-config.properties`:

```properties
openrune.jdbc.url=jdbc:postgresql://127.0.0.1:5432/openrune
openrune.db.user=openrune
openrune.db.password=openrune
openrune.worldsLinkPort=9091
```

Game server: `central.same-instance: true` (or equivalent) with empty JDBC so the game brings up or reuses that Postgres, **and** the same URL for Central-linked features. Central does not start Postgres; it only connects.

### Properties and environment variables

| Property | Environment variable | Purpose |
| --- | --- | --- |
| `openrune.jdbc.url` | `OPENRUNE_JDBC_URL` | PostgreSQL JDBC URL |
| `openrune.db.user` | `OPENRUNE_DB_USER` | DB user |
| `openrune.db.password` | `OPENRUNE_DB_PASSWORD` | DB password |
| `openrune.db.poolSize` | `OPENRUNE_DB_POOL_SIZE` | Hikari maximum pool size (clamped 1–100) |
| `openrune.sessionsTtlMs` | `OPENRUNE_SESSION_TTL_MS` | Stale session sweep TTL (ms) |
| `openrune.worldsLinkPort` | `OPENRUNE_WORLD_LINK_PORT` | Worlds-link TCP port; use `false` or `0` to disable the listener |
| `openrune.worldsLinkSoBacklog` | `OPENRUNE_WORLD_LINK_SO_BACKLOG` | TCP listen backlog |
| `openrune.worldsLinkReadTimeoutSeconds` | `OPENRUNE_WORLD_LINK_READ_TIMEOUT_SEC` | Read timeout for world connections |
| `openrune.worldsLinkMaxConnectionsPerIp` | `OPENRUNE_WORLD_LINK_MAX_CONN_PER_IP` | Per-IP connection cap |
| `openrune.worldsLinkMaxConnectionsTotal` | `OPENRUNE_WORLD_LINK_MAX_CONN_TOTAL` | Global connection cap |
| `openrune.worldsLinkHandlerThreads` | `OPENRUNE_WORLD_LINK_HANDLER_THREADS` | Worker threads for world-link |
| `openrune.worldsLinkHandlerQueueSize` | `OPENRUNE_WORLD_LINK_HANDLER_QUEUE` | Handler queue size |
| `openrune.worldsLinkMaxFramesPerSecond` | `OPENRUNE_WORLD_LINK_MAX_FRAMES_PER_SEC` | Rate limit (frames/sec) |
| `openrune.worldsLinkMaxFrameBurst` | `OPENRUNE_WORLD_LINK_MAX_FRAME_BURST` | Burst allowance for rate limit |
| `openrune.onlineSampleIntervalSeconds` | `OPENRUNE_ONLINE_SAMPLE_INTERVAL_SEC` | Interval for `online_samples` (seconds) |
| `openrune.http.port` | `OPENRUNE_HTTP_PORT` | HTTP port (applied before Ktor engine start) |
| `openrune.javConfig.revision` | `OPENRUNE_JAV_CONFIG_REVISION` | Remote jav config revision (default `238`) |
| `openrune.javConfig.remoteUrlTemplate` | `OPENRUNE_JAV_CONFIG_URL_TEMPLATE` | Download URL with `%d` for revision |
| `openrune.javConfig.configProps.*` | — | Per-key overrides (`param.25`, `msg.ok`, or multiline `openrune.javConfig.configProps`) |
| `openrune.javConfig.refreshMinutes` | `OPENRUNE_JAV_CONFIG_REFRESH_MINUTES` | Cache refresh interval |
| `openrune.javConfig.httpTimeoutSeconds` | `OPENRUNE_JAV_CONFIG_HTTP_TIMEOUT_SEC` | HTTP timeout for remote jav config fetch |

World-link keys use the **`worldsLink`** spelling (`openrune.worldsLinkPort`, etc.), matching `CentralRuntimeConfig.kt`.

## HTTP API (machine-oriented)

Examples (default Ktor port may be 8080; adjust to your deployment):

- **`GET /worldslist.ws`**, **`GET /worlds.js`** — cached world list payloads for clients
- **`GET /jav_config.ws`** — proxied Jagex jav config from `openrune.javConfig.remoteUrlTemplate` (revision in `openrune.javConfig.revision`); optional `openrune.javConfig.configProps.*` overrides

## Admin website (`/admin`)

The bundled SPA under **`/admin`** is a small **SQLite-in-the-browser** admin experiment served as static files. In practice it is meant for quick local inspection and demos, not a supported production console.

**What it can do (when you use it):**

- Browse and edit **realms** and **worlds** (including login gate fields and related metadata surfaced in the UI).
- Browse **accounts** (pagination / simple search depending on the build).
- Manage per-world **login whitelist** rows where exposed in the Worlds UI.

**Disclaimer:** The admin site was **vibe-coded as a test**. It is **not guaranteed** for correctness, security review, ongoing support, or fixes. Prefer direct SQL, migrations, or a proper operations workflow for anything serious. Use at your own risk.

## Building

Use the Gradle wrapper from the repo root (Java and PostgreSQL required for running tests that hit embedded Postgres).

```bash
./gradlew :openrune-central:build
```

## Docker

The `Dockerfile` is **runtime-only**: it does not compile the project. Build the shadow JAR, copy it next to the Dockerfile (default name `openrune-central-server.jar`), then build the image.

```bash
./gradlew :openrune-central:shadowJar
cp openrune-central/build/libs/openrune-central-server.jar .
docker build -t openrune-central .
docker run --rm -p 8080:8080 -p 9091:9091 \
  -e OPENRUNE_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/openrune_central \
  -e OPENRUNE_DB_USER=openrune -e OPENRUNE_DB_PASSWORD=openrune \
  openrune-central
```

If your JAR uses another name: `docker build --build-arg JAR_FILE=my.jar -t openrune-central .`

Optional JVM tuning: `-e JAVA_OPTS=-Xmx512m` (the entrypoint expands `JAVA_OPTS`).

## Pterodactyl

Import `deploy/pterodactyl/egg-openrune-central.json` into a nest (Panel: **Nests** → your nest → **Import Egg**). **There is no JAR download** — build `openrune-central-server.jar` with Gradle, upload it to the server (and `central-config.properties` if you use a file), then run **Reinstall** (checks the JAR exists) and **Start**. The egg uses Java 21, sets `OPENRUNE_HTTP_PORT` from the primary allocation, and treats **`OpenRune Central is online`** (and Ktor’s **`Application started`** line) as startup-complete so the server shows **Running**.
