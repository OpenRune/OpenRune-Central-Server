#!/usr/bin/env bash
# Runs startup modules (logcleaner → cloudflared) before the Java process.
set -euo pipefail

cd /home/container

BLUE='\033[0;34m'
BOLD_BLUE='\033[1;34m'
NC='\033[0m'

header() {
    echo -e "${BLUE}───────────────────────────────────────────────${NC}"
    echo -e "${BOLD_BLUE}[Orchestrator] $1${NC}"
}

run_module() {
    local name="$1"
    local script="modules/${name}/start.sh"
    if [[ ! -f "${script}" ]]; then
        echo "[Orchestrator] Module '${name}' not found (${script}); skipping."
        return 0
    fi
    header "Running module: ${name}"
    # shellcheck source=/dev/null
    source "${script}"
}

MODULE_ORDER="${START_MODULES:-autoupdate config logcleaner cloudflared}"
for module in ${MODULE_ORDER}; do
    run_module "${module}"
done

header "Starting OpenRune Central (Java)"
