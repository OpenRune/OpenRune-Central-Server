# OpenRune Central — Pterodactyl egg

Import `egg-openrune-central.json` into your panel nest.

## Configuration from the panel

On each start the **config** module writes `central-config.yaml` from Startup variables (`OPENRUNE_WRITE_CONFIG=1` by default). Set **`OPENRUNE_WRITE_CONFIG=0`** to use your own `central-config.yaml` (same as running without the egg). Env vars are the base layer; YAML overrides when set.

**Database** must be reachable: set `OPENRUNE_JDBC_URL` or `OPENRUNE_DB_HOST` + `OPENRUNE_DB_NAME`. User/password are required unless you use a JDBC URL only and set `OPENRUNE_DB_REQUIRE_CREDENTIALS=false` (trust/peer auth).

| Variable | Example |
|----------|---------|
| `OPENRUNE_HTTP_PORT` | `9090` |
| `OPENRUNE_DB_HOST` | `db.example.com` |
| `OPENRUNE_DB_PORT` | `5432` |
| `OPENRUNE_DB_NAME` | `openrune_central` |
| `OPENRUNE_DB_USER` / `OPENRUNE_DB_PASSWORD` | … |
| `OPENRUNE_DB_POOL_SIZE` | `10` |
| `OPENRUNE_JDBC_URL` | *(optional full override)* |
| `OPENRUNE_WORLD_LINK_PORT` | `9091` |
| `OPENRUNE_JAV_CONFIG_PROPS` | Multiline jav lines (see below) |

Set `OPENRUNE_WRITE_CONFIG=0` if you upload your own `central-config.yaml` and do not want it overwritten.

### Jav config overrides

Paste the same lines as in a `jav_config.ws` file into **Jav config overrides**:

```
title=Fluxious
codebase=http://w1.example.com/
cachedir=fluxious
param=25=238
param=17=http://central.example.com:9090/worldslist.ws
msg=ok=OK
```

These map to `openrune.javConfig.configProps.*` and override the downloaded remote jav config. Environment variable `OPENRUNE_JAV_CONFIG_PROPS` also works at runtime (overrides the file).

## Auto-update (GitHub commit)

See previous section — `autoupdate` module syncs scripts when `main` moves.

## Modules (startup order)

`autoupdate` → `config` → `logcleaner` → `cloudflared` → Java

## Console

`!help`, `stop`, `refreshcache`, `refresh all`, `refresh worlds`, …
