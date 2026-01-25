## OpenRune Central Server

Central HTTP service used by clients/tools (public API).

Default bind: `0.0.0.0:8080` (see `central/src/main/resources/application.conf`).

### Running

#### Run with Gradle

```powershell
.\gradlew.bat :central:run
```

The server loads `config.yml` from the working directory; if not found, it searches parent directories for `config.yml`.

#### Run embedded (from another JVM/app)

If you embed the central server inside another app, use `dev.openrune.central.CentralServer.start(...)`:

```kotlin
val engine = CentralServer.start(
    configPath = Paths.get("C:/path/to/central-server.yml"),
    rev = 235
)
```

`CentralServer.start(...)` starts Netty in the background and blocks only until Ktor reports the application has started.

### Configuration (config.yml)

Required keys:

- `rev` (int)
- `name` (string)
- `websiteUrl` (string)
- `storage` (object)
- `worlds` (list)

Storage backends:

- `storage.type: flat_gson`
- `storage.type: mongo`
- `storage.type: postgres`

### Public HTTP API (no auth)

Base path: `/api/public`

| Method | Path | Description |
|---|---|---|
| GET | `/api/public/health` | Health check |
| GET | `/api/public/worlds` | List worlds (includes `playersOnline`) |
| GET | `/api/public/worlds/{id}` | World by id |
| GET | `/api/public/players/world` | Players online per world (cached) |
| GET | `/api/public/players/world/{id}` | Players online for one world |

Other public endpoints:

| Method | Path | Description |
|---|---|---|
| GET | `/worldlist.ws` | Worldlist binary payload |
| GET | `/java_local.ws` | Client bootstrap `java_local.ws` |

### Key generation

Run `dev.openrune.central.tools.KeyGenKt` to generate an Ed25519 keypair:

- Central stores the public key in `config.yml` under `worlds[].authPublicKey`
- World server stores the private key (`authPrivateKey`) and uses it for authentication


