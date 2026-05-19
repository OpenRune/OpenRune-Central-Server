#!/bin/bash
# Pterodactyl install container script (referenced from egg JSON).
set -euo pipefail

apk add --no-cache curl bash >/dev/null 2>&1
cd /mnt/server

ARCH=$(uname -m)
case "${ARCH}" in
  x86_64|amd64) CF_ARCH=amd64 ;;
  aarch64|arm64) CF_ARCH=arm64 ;;
  *) echo "[Install] Unsupported arch: ${ARCH}"; exit 1 ;;
esac

BASE="https://raw.githubusercontent.com/OpenRune/OpenRune-Central-Server/main/deploy/pterodactyl"

echo "[Install] Downloading cloudflared (${CF_ARCH})..."
curl -fsSL "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${CF_ARCH}" -o cloudflared
chmod +x cloudflared

fetch() {
  local path="$1"
  echo "[Install] Downloading ${path}..."
  mkdir -p "$(dirname "${path}")"
  curl -fsSL "${BASE}/${path}" -o "${path}"
}

fetch autoupdate-files.txt
fetch start.sh
fetch start-modules.sh
chmod +x start.sh start-modules.sh
fetch modules/config/start.sh
fetch modules/autoupdate/start.sh
fetch modules/logcleaner/start.sh
fetch modules/cloudflared/start.sh
chmod +x modules/config/start.sh modules/autoupdate/start.sh modules/logcleaner/start.sh modules/cloudflared/start.sh

if [[ ! -f /mnt/server/openrune-central-server.jar ]]; then
  echo "[Install] Warning: upload your server JAR (default name: openrune-central-server.jar) before starting."
fi

echo "[Install] Done."
