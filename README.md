## OpenRune Central Server (Ktor)

Central HTTP server for things like login, PMs, and world status (players online per world).

### APIs

- **Public API**: no auth required
- **Private API**: world servers authenticate using world id + public/private keys

### Running

- **Windows (PowerShell)**:

```powershell
.\gradlew.bat :central:run
```

Before starting, edit the root `config.yml` (required).

Server runs on `http://localhost:8081`.

On first run it will create `java_local.ws` (if missing) (do not commit if you treat it as generated).

### config.yml

Required keys:

- `rev`
- `name`
- `websiteUrl`
- `storage`
- `worlds`

### Public API examples

- **List worlds**

`GET /api/public/worlds`

- **World by id**

`GET /api/public/worlds/world-1`

### Client bootstrap files

- **java_local.ws** (plain text)

`GET /java_local.ws`

The server will create `java_local.ws` in the repo root on startup if missing, by downloading
`https://client.blurite.io/jav_local_{rev}.ws` (using your `config.yml` `rev`) and patching:

- `title` → `config.yml` `name`
- `cachedir` → lowercased `name` (alphanumeric only)
- `codebase` → first `World` entry address
- `param=25` → `rev`
- `param=17` → `{websiteUrl}/worldlist.ws`
- `param=13` → `.{websiteUrl host}` (replaces `.runescape.com`)

### Private API auth headers

Every private request must include:

- `X-World-Id`
- `X-Timestamp`
- `X-Signature`

Public keys are stored in `config.yml` under `worlds[].authPublicKey`.

### Private API examples

- **Login request** (stubbed for now; always allowed)

`POST /api/private/login/request`

```powershell
$worldId = 1
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

# Body that will be signed
$body = (@{ username="bob"; password="test" } | ConvertTo-Json -Compress)

# You generate keys via KeyGen (see below) and the WORLD server keeps authPrivateKey.
# This repo (central) stores authPublicKey in config.yml.
# Signing example is world-server-side and depends on your runtime; implement Ed25519 sign of:
# "{timestamp}\n{worldId}\nPOST\n/api/private/login/request\n{body}"
$signature = "TODO"

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/private/login/request" `
  -Headers @{
    "X-World-Id" = "$worldId"
    "X-Timestamp" = "$timestamp"
    "X-Signature" = "$signature"
  } `
  -ContentType "application/json" `
  -Body $body
```

### Key generation (world/private + central/public)

Run `dev.openrune.central.tools.KeyGenKt` to generate a matching Ed25519 keypair:
- **Central**: put the printed `authPublicKey` into `config.yml` under the world.
- **World server**: keep the printed private key secret (`authPrivateKey`) and use it to sign private API requests.


