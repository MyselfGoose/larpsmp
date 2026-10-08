#!/usr/bin/env bash
# LarpSMP one-command bootstrap for CachyOS / Arch.
# Audits the machine, installs missing deps, starts Postgres + pgAdmin,
# syncs plugin DB config, builds + tests, then launches Paper.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_SERVER_DIR="${ROOT_DIR}/dev-server"
COMPOSE_FILE="${ROOT_DIR}/docker/docker-compose.yml"
ENV_FILE="${ROOT_DIR}/.env"
ENV_EXAMPLE="${ROOT_DIR}/.env.example"
SOURCE_CONFIG="${ROOT_DIR}/src/main/resources/config.yml"
RUNTIME_CONFIG_DIR="${DEV_SERVER_DIR}/plugins/MoneyEvent"
RUNTIME_CONFIG="${RUNTIME_CONFIG_DIR}/config.yml"
PGPASS_FILE="${ROOT_DIR}/docker/pgadmin/pgpass"
SERVERS_JSON="${ROOT_DIR}/docker/pgadmin/servers.json"

PAPER_VERSION="1.21.11"
MIN_JAVA_MAJOR=21
PROJECT_JDK="${ROOT_DIR}/tools/jdk-25"
PAPER_HOST_PORT=25565

# Defaults — overridden by project-root .env after load_dotenv().
POSTGRES_HOST_PORT=5433
PGADMIN_HOST_PORT=5050
DB_NAME="larpsmp"
DB_USER="larpsmp"
DB_PASSWORD="larpsmp"
PGADMIN_EMAIL="admin@larpsmp.dev"
PGADMIN_PASSWORD="admin"
JDBC_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_HOST_PORT}/${DB_NAME}"

CURRENT_PHASE="preflight"
NEED_PACKAGES=()
DOCKER_BIN=""
COMPOSE_WRAPPER=()
JAVA_HOME_RESOLVED=""

# Rootless Docker installs the CLI under ~/bin and the socket under XDG_RUNTIME_DIR.
# Ensure both are visible before any audits so re-runs recognize our own stack.
export PATH="${HOME}/bin:${PATH:-/usr/bin:/bin}"
if [[ -z "${DOCKER_HOST:-}" && -S "/run/user/$(id -u)/docker.sock" ]]; then
  export DOCKER_HOST="unix:///run/user/$(id -u)/docker.sock"
fi

# ---------------------------------------------------------------------------
# .env (single secrets file for DB, pgAdmin, future API keys)
# ---------------------------------------------------------------------------

ensure_env_file() {
  if [[ -f "${ENV_FILE}" ]]; then
    return 0
  fi
  if [[ -f "${ENV_EXAMPLE}" ]]; then
    cp "${ENV_EXAMPLE}" "${ENV_FILE}"
    log_info "Created ${ENV_FILE} from .env.example — edit this file for shared secrets."
    return 0
  fi
  die "Missing .env and .env.example. Add project-root secrets before continuing."
}

# Load KEY=VALUE from .env into the current shell (export). Does not override
# variables already set in the process environment.
load_dotenv() {
  local file="${1:-${ENV_FILE}}"
  [[ -f "${file}" ]] || return 0
  local line key value
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    if [[ "${line}" == export\ * ]]; then
      line="${line#export }"
      line="${line#"${line%%[![:space:]]*}"}"
    fi
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    if [[ -n "${!key+x}" ]]; then
      continue
    fi
    # Strip matching single/double quotes.
    if [[ "${value}" =~ ^\"(.*)\"$ ]]; then
      value="${BASH_REMATCH[1]}"
    elif [[ "${value}" =~ ^\'(.*)\'$ ]]; then
      value="${BASH_REMATCH[1]}"
    else
      value="${value%%\#*}"
      value="${value%"${value##*[![:space:]]}"}"
      value="${value#"${value%%[![:space:]]*}"}"
    fi
    export "${key}=${value}"
  done < "${file}"
}

apply_env_settings() {
  DB_NAME="${LARPSMP_POSTGRES_DB:-${DB_NAME}}"
  DB_USER="${LARPSMP_DB_USER:-${LARPSMP_POSTGRES_USER:-${DB_USER}}}"
  DB_PASSWORD="${LARPSMP_DB_PASSWORD:-${LARPSMP_POSTGRES_PASSWORD:-${DB_PASSWORD}}}"
  POSTGRES_HOST_PORT="${LARPSMP_POSTGRES_PORT:-${POSTGRES_HOST_PORT}}"
  PGADMIN_HOST_PORT="${LARPSMP_PGADMIN_PORT:-${PGADMIN_HOST_PORT}}"
  PGADMIN_EMAIL="${LARPSMP_PGADMIN_EMAIL:-${PGADMIN_EMAIL}}"
  PGADMIN_PASSWORD="${LARPSMP_PGADMIN_PASSWORD:-${PGADMIN_PASSWORD}}"
  JDBC_URL="${LARPSMP_JDBC_URL:-jdbc:postgresql://127.0.0.1:${POSTGRES_HOST_PORT}/${DB_NAME}}"

  # Ensure Compose / plugin see a consistent exported set even if .env omitted some keys.
  export LARPSMP_POSTGRES_DB="${DB_NAME}"
  export LARPSMP_POSTGRES_USER="${LARPSMP_POSTGRES_USER:-${DB_USER}}"
  export LARPSMP_POSTGRES_PASSWORD="${LARPSMP_POSTGRES_PASSWORD:-${DB_PASSWORD}}"
  export LARPSMP_POSTGRES_PORT="${POSTGRES_HOST_PORT}"
  export LARPSMP_DB_USER="${DB_USER}"
  export LARPSMP_DB_PASSWORD="${DB_PASSWORD}"
  export LARPSMP_JDBC_URL="${JDBC_URL}"
  export LARPSMP_PGADMIN_EMAIL="${PGADMIN_EMAIL}"
  export LARPSMP_PGADMIN_PASSWORD="${PGADMIN_PASSWORD}"
  export LARPSMP_PGADMIN_PORT="${PGADMIN_HOST_PORT}"
}

sync_pgadmin_secrets() {
  mkdir -p "$(dirname "${PGPASS_FILE}")"
  # Format: hostname:port:database:username:password (Compose-internal host/port).
  printf 'postgres:5432:%s:%s:%s\n' \
    "${DB_NAME}" \
    "${LARPSMP_POSTGRES_USER:-${DB_USER}}" \
    "${LARPSMP_POSTGRES_PASSWORD:-${DB_PASSWORD}}" > "${PGPASS_FILE}"
  chmod 600 "${PGPASS_FILE}" 2>/dev/null || true

  cat > "${SERVERS_JSON}" <<EOF
{
  "Servers": {
    "1": {
      "Name": "larpsmp",
      "Group": "Servers",
      "Host": "postgres",
      "Port": 5432,
      "MaintenanceDB": "${DB_NAME}",
      "Username": "${LARPSMP_POSTGRES_USER:-${DB_USER}}",
      "SSLMode": "prefer",
      "PassFile": "/pgpass"
    }
  }
}
EOF
}

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

log_info()  { printf '==> %s\n' "$*"; }
log_ok()    { printf '  [OK]   %s\n' "$*"; }
log_warn()  { printf '  [WARN] %s\n' "$*" >&2; }
log_fail()  { printf '  [FAIL] %s\n' "$*" >&2; }
log_phase() {
  CURRENT_PHASE="$1"
  printf '\n======== Phase: %s ========\n' "$1"
}

die() {
  log_fail "Bootstrap failed during phase '${CURRENT_PHASE}': $*"
  log_fail "Fix the issue above, then re-run: ./scripts/dev-server.sh"
  exit 1
}

on_err() {
  local exit_code=$?
  log_fail "Unexpected error (exit ${exit_code}) during phase '${CURRENT_PHASE}'."
  log_fail "Re-run ./scripts/dev-server.sh after fixing the problem."
  exit "${exit_code}"
}
trap on_err ERR

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

have_cmd() {
  command -v "$1" >/dev/null 2>&1
}

port_in_use() {
  local port="$1"
  if have_cmd ss; then
    ss -tlnH "sport = :${port}" 2>/dev/null | grep -q .
    return $?
  fi
  if have_cmd lsof; then
    lsof -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
    return $?
  fi
  return 1
}

list_paper_java_pids() {
  local pid cmd
  for pid in $(pgrep -f "paper-${PAPER_VERSION}-.*\\.jar" 2>/dev/null || true); do
    cmd="$(ps -p "${pid}" -o comm= 2>/dev/null || true)"
    if [[ "${cmd}" == "java" ]]; then
      printf '%s\n' "${pid}"
    fi
  done
}

is_our_paper_port() {
  [[ -n "$(list_paper_java_pids)" ]]
}

remove_larpsmp_containers() {
  # Always force-remove by exact name — covers created/exited/dead leftovers.
  local name
  for name in larpsmp-pgadmin larpsmp-postgres; do
    docker_cmd rm -f "${name}" >/dev/null 2>&1 || true
  done
}

remove_larpsmp_networks() {
  # Stale project networks cause "No such container" dependency races on rootless Docker.
  local net
  for net in docker_larpsmp-net larpsmp_larpsmp-net; do
    docker_cmd network rm "${net}" >/dev/null 2>&1 || true
  done
  # Also drop any leftover networks labeled for this compose project.
  local id
  while IFS= read -r id; do
    [[ -n "${id}" ]] || continue
    docker_cmd network rm "${id}" >/dev/null 2>&1 || true
  done < <(docker_cmd network ls --filter "name=larpsmp" --format '{{.ID}}' 2>/dev/null || true)
}

wait_port_free() {
  local port="$1"
  local seconds="${2:-30}"
  local i
  for i in $(seq 1 "${seconds}"); do
    if ! port_in_use "${port}"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

can_sudo() {
  if [[ "$(id -u)" -eq 0 ]]; then
    return 0
  fi
  if ! have_cmd sudo; then
    return 1
  fi
  # May prompt for password; that is intentional.
  sudo -v
}

run_sudo() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  else
    sudo "$@"
  fi
}

# ---------------------------------------------------------------------------
# Docker invocation (supports system docker, rootless, and sg docker)
# ---------------------------------------------------------------------------

docker_available() {
  # Prefer explicit rootless binary if PATH is incomplete.
  if [[ -x "${HOME}/bin/docker" ]]; then
    export PATH="${HOME}/bin:${PATH}"
  fi
  if ! have_cmd docker; then
    return 1
  fi
  # Prefer an already-configured rootless socket if present and working.
  if [[ -S "/run/user/$(id -u)/docker.sock" ]]; then
    if DOCKER_HOST="unix:///run/user/$(id -u)/docker.sock" docker info >/dev/null 2>&1; then
      export DOCKER_HOST="unix:///run/user/$(id -u)/docker.sock"
      DOCKER_BIN="$(command -v docker)"
      COMPOSE_WRAPPER=()
      return 0
    fi
  fi
  if docker info >/dev/null 2>&1; then
    DOCKER_BIN="$(command -v docker)"
    COMPOSE_WRAPPER=()
    return 0
  fi
  # Fresh install: user is in the docker group in /etc/group but this shell lacks it.
  if have_cmd sg && getent group docker 2>/dev/null | grep -qw "$(id -un)"; then
    if sg docker -c 'docker info' >/dev/null 2>&1; then
      DOCKER_BIN="$(command -v docker)"
      COMPOSE_WRAPPER=(sg docker -c)
      return 0
    fi
  fi
  return 1
}

docker_cmd() {
  if [[ ${#COMPOSE_WRAPPER[@]} -gt 0 ]]; then
    # shellcheck disable=SC2145
    sg docker -c "docker $*"
  else
    docker "$@"
  fi
}

compose_cmd() {
  local args=("$@")
  local env_file_args=()
  if [[ -f "${ENV_FILE}" ]]; then
    env_file_args=(--env-file "${ENV_FILE}")
  fi
  if [[ ${#COMPOSE_WRAPPER[@]} -gt 0 ]]; then
    local joined=""
    local a
    for a in "${env_file_args[@]}" "${args[@]}"; do
      joined+=" $(printf '%q' "$a")"
    done
    sg docker -c "docker compose -f $(printf '%q' "${COMPOSE_FILE}")${joined}"
  else
    docker compose "${env_file_args[@]}" -f "${COMPOSE_FILE}" "${args[@]}"
  fi
}

compose_available() {
  docker_cmd compose version >/dev/null 2>&1
}

# ---------------------------------------------------------------------------
# Java
# ---------------------------------------------------------------------------

resolve_java() {
  local candidates=()
  if [[ -n "${JAVA_HOME:-}" ]]; then
    candidates+=("${JAVA_HOME}")
  fi
  candidates+=(
    "/usr/lib/jvm/java-21-openjdk"
    "/usr/lib/jvm/java-25-openjdk"
    "${PROJECT_JDK}"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -x "${candidate}/bin/java" ]]; then
      echo "${candidate}"
      return 0
    fi
  done
  return 1
}

java_major_of() {
  local home="$1"
  "${home}/bin/java" -XshowSettings:properties -version 2>&1 \
    | awk -F'= ' '/java.specification.version/ {print $2; exit}' \
    | tr -d '[:space:]'
}

# ---------------------------------------------------------------------------
# Phase 0 — Preflight
# ---------------------------------------------------------------------------

phase_preflight() {
  log_phase "preflight"

  ensure_env_file
  load_dotenv "${ENV_FILE}"
  apply_env_settings
  sync_pgadmin_secrets
  log_ok "Secrets loaded from ${ENV_FILE}"

  cat <<EOF
LarpSMP development bootstrap
------------------------------
This script will:
  1. Audit and install host packages (Java, Docker, curl, python, git)
  2. Stop any previous Paper / Postgres / pgAdmin from this project
  3. Start Postgres + pgAdmin via Docker Compose (credentials from .env)
  4. Sync plugin DB config from .env
  5. Build + test the plugin
  6. Start Paper ${PAPER_VERSION} on localhost:${PAPER_HOST_PORT}

Target OS: CachyOS / Arch (pacman). Re-runs reclaim ports ${POSTGRES_HOST_PORT}/${PGADMIN_HOST_PORT}/${PAPER_HOST_PORT} automatically.
EOF

  [[ -f "${ROOT_DIR}/build.gradle.kts" ]] || die "Missing build.gradle.kts — run from a full git checkout."
  [[ -f "${COMPOSE_FILE}" ]] || die "Missing docker/docker-compose.yml."
  [[ -f "${SOURCE_CONFIG}" ]] || die "Missing src/main/resources/config.yml."
  [[ -f "${ENV_EXAMPLE}" ]] || die "Missing .env.example."
  [[ -f "${ENV_FILE}" ]] || die "Missing .env (should have been created from .env.example)."
  [[ -f "${SERVERS_JSON}" ]] || die "Missing docker/pgadmin/servers.json after sync."
  [[ -f "${PGPASS_FILE}" ]] || die "Missing docker/pgadmin/pgpass after sync."
  [[ -x "${ROOT_DIR}/gradlew" ]] || die "gradlew is missing or not executable."

  if ! have_cmd pacman; then
    die "pacman not found. This bootstrap targets CachyOS/Arch only.
Install Java 21+, Docker, and Compose manually on other OSes, then use docker compose + ./gradlew build."
  fi

  log_ok "Repository layout looks valid."
  log_ok "Package manager: pacman (Arch/CachyOS)."
}

# ---------------------------------------------------------------------------
# Phase 1 — Audit
# ---------------------------------------------------------------------------

phase_audit() {
  log_phase "audit"
  NEED_PACKAGES=()

  if have_cmd git; then log_ok "git present"; else log_warn "git missing"; NEED_PACKAGES+=("git"); fi
  if have_cmd curl; then log_ok "curl present"; else log_warn "curl missing"; NEED_PACKAGES+=("curl"); fi
  if have_cmd python3; then log_ok "python3 present"; else log_warn "python3 missing"; NEED_PACKAGES+=("python"); fi

  if JAVA_HOME_RESOLVED="$(resolve_java)"; then
    local major
    major="$(java_major_of "${JAVA_HOME_RESOLVED}")"
    if [[ "${major}" -ge "${MIN_JAVA_MAJOR}" ]]; then
      log_ok "Java ${major} at ${JAVA_HOME_RESOLVED}"
    else
      log_warn "Java ${major} too old at ${JAVA_HOME_RESOLVED}"
      NEED_PACKAGES+=("jdk21-openjdk")
      JAVA_HOME_RESOLVED=""
    fi
  else
    log_warn "Java ${MIN_JAVA_MAJOR}+ missing"
    NEED_PACKAGES+=("jdk21-openjdk")
  fi

  if have_cmd docker; then
    log_ok "docker CLI present ($(command -v docker))"
  else
    log_warn "docker CLI missing"
    NEED_PACKAGES+=("docker")
  fi

  # Probe Docker early so port ownership checks can see our containers.
  if have_cmd docker && docker_available; then
    if compose_available; then
      log_ok "Docker daemon reachable + compose available"
    else
      log_warn "docker compose missing"
      NEED_PACKAGES+=("docker-compose")
    fi
  else
    log_warn "Docker daemon not reachable yet (will start/fix)"
    if have_cmd docker; then
      # CLI exists but compose plugin may still be missing.
      if ! docker compose version >/dev/null 2>&1 && [[ ! -x "${HOME}/.docker/cli-plugins/docker-compose" ]]; then
        NEED_PACKAGES+=("docker-compose")
      fi
    fi
  fi

  local mode
  mode="$(stat -c '%a' "${PGPASS_FILE}" 2>/dev/null || echo '?')"
  if [[ "${mode}" == "600" ]]; then
    log_ok "pgpass permissions are 600"
  else
    log_warn "pgpass permissions are ${mode} (will chmod 600)"
  fi

  for port_spec in \
    "${POSTGRES_HOST_PORT}:postgres" \
    "${PGADMIN_HOST_PORT}:pgadmin" \
    "${PAPER_HOST_PORT}:paper"
  do
    local port="${port_spec%%:*}"
    local kind="${port_spec##*:}"
    if ! port_in_use "${port}"; then
      log_ok "Port ${port} is free (${kind})"
      continue
    fi
    # Do not fail here — phase_reclaim stops Paper + our Compose stack and frees ports.
    log_warn "Port ${port} is in use (${kind}); will reclaim previous LarpSMP processes"
  done

  if [[ ${#NEED_PACKAGES[@]} -eq 0 ]]; then
    log_ok "No host packages need installing."
  else
    # Deduplicate
    local -A seen=()
    local unique=()
    local pkg
    for pkg in "${NEED_PACKAGES[@]}"; do
      [[ -n "${seen[$pkg]+x}" ]] && continue
      seen[$pkg]=1
      unique+=("${pkg}")
    done
    NEED_PACKAGES=("${unique[@]}")
    log_info "Packages to install: ${NEED_PACKAGES[*]}"
  fi
}

# ---------------------------------------------------------------------------
# Phase 2 — Remediate
# ---------------------------------------------------------------------------

phase_remediate() {
  log_phase "remediate"

  if [[ ${#NEED_PACKAGES[@]} -gt 0 ]]; then
    if ! can_sudo; then
      die "Need sudo to install: ${NEED_PACKAGES[*]}

Run:
  sudo pacman -S --needed ${NEED_PACKAGES[*]}
  sudo systemctl enable --now docker
  sudo usermod -aG docker \"\$USER\"
Then log out/in (or re-run this script; it can use 'sg docker')."
    fi
    log_info "Installing with pacman: ${NEED_PACKAGES[*]}"
    run_sudo pacman -S --needed --noconfirm "${NEED_PACKAGES[@]}"
  else
    log_ok "Skipping pacman install."
  fi

  chmod 600 "${PGPASS_FILE}"
  log_ok "Ensured pgpass mode 600."

  # Prefer existing working Docker (including rootless). Start daemons as needed.
  if ! docker_available; then
    # Rootless user service (installed via get.docker.com/rootless).
    if [[ -f "${HOME}/.config/systemd/user/docker.service" ]]; then
      log_info "Starting rootless Docker user service..."
      systemctl --user enable --now docker.service >/dev/null 2>&1 || true
      export DOCKER_HOST="unix:///run/user/$(id -u)/docker.sock"
      sleep 2
    fi
  fi

  if docker_available && compose_available; then
    log_ok "Using existing Docker setup."
  else
    if ! have_cmd docker; then
      die "docker CLI still missing after install.
Expected at $(command -v docker 2>/dev/null || echo 'not in PATH') or ${HOME}/bin/docker."
    fi
    if can_sudo; then
      log_info "Enabling system Docker service..."
      run_sudo systemctl enable --now docker || true
      if ! id -nG | grep -qw docker; then
        log_info "Adding ${USER} to docker group..."
        run_sudo usermod -aG docker "${USER}"
      fi
    fi
    sleep 2
    if ! docker_available; then
      die "Docker daemon is not reachable.
If you were just added to the docker group, re-run this script (it uses 'sg docker').
For rootless Docker: systemctl --user start docker.service"
    fi
    if ! compose_available; then
      die "docker compose is not available. Install the docker-compose package and re-run."
    fi
    log_ok "Docker daemon is ready."
  fi

  if ! JAVA_HOME_RESOLVED="$(resolve_java)"; then
    die "Java ${MIN_JAVA_MAJOR}+ still not found after remediation."
  fi
  export JAVA_HOME="${JAVA_HOME_RESOLVED}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
  local major
  major="$(java_major_of "${JAVA_HOME}")"
  if [[ "${major}" -lt "${MIN_JAVA_MAJOR}" ]]; then
    die "Expected Java ${MIN_JAVA_MAJOR}+, found Java ${major} at ${JAVA_HOME}"
  fi
  log_ok "Using Java ${major} from ${JAVA_HOME}"
}

# ---------------------------------------------------------------------------
# Phase 2b — Reclaim previous Paper / Compose processes and ports
# ---------------------------------------------------------------------------

stop_paper_processes() {
  local pids
  pids="$(list_paper_java_pids | tr '\n' ' ')"
  if [[ -z "${pids// /}" ]]; then
    log_ok "No previous Paper Java process running."
    return 0
  fi
  log_info "Stopping previous Paper process(es): ${pids}"
  # shellcheck disable=SC2086
  kill -TERM ${pids} 2>/dev/null || true
  local i pid
  for i in $(seq 1 40); do
    if [[ -z "$(list_paper_java_pids)" ]]; then
      log_ok "Previous Paper stopped."
      return 0
    fi
    sleep 1
  done
  for pid in $(list_paper_java_pids); do
    kill -KILL "${pid}" 2>/dev/null || true
  done
  log_ok "Previous Paper force-stopped."
}

stop_larpsmp_stack() {
  if ! docker_available; then
    log_warn "Docker not available yet; cannot stop Compose containers this pass."
    return 0
  fi

  log_info "Stopping LarpSMP Compose stack (Postgres + pgAdmin)..."
  if compose_available; then
    # Tear down project containers + default network. Keep volumes (account data).
    compose_cmd down --remove-orphans >/dev/null 2>&1 || true
  fi
  remove_larpsmp_containers
  remove_larpsmp_networks
  # Give rootless Docker a beat to drop port bindings / ghost container refs.
  sleep 2
  log_ok "LarpSMP Docker stack stopped."
}

ensure_ports_free_or_die() {
  local port kind
  for port_spec in \
    "${POSTGRES_HOST_PORT}:Postgres" \
    "${PGADMIN_HOST_PORT}:pgAdmin" \
    "${PAPER_HOST_PORT}:Paper"
  do
    port="${port_spec%%:*}"
    kind="${port_spec##*:}"
    if port_in_use "${port}"; then
      if ! wait_port_free "${port}" 15; then
        die "Port ${port} (${kind}) is still in use after reclaiming LarpSMP processes.
Identify/stop the foreign process (ss -tlnp | grep ${port}), then re-run."
      fi
    fi
    log_ok "Port ${port} is free (${kind})"
  done
}

phase_reclaim() {
  log_phase "reclaim"

  stop_paper_processes
  stop_larpsmp_stack
  ensure_ports_free_or_die
}

# ---------------------------------------------------------------------------
# Phase 3 — Compose stack
# ---------------------------------------------------------------------------

wait_for_postgres() {
  local i
  for i in $(seq 1 60); do
    if compose_cmd exec -T postgres pg_isready -U "${DB_USER}" -d "${DB_NAME}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_for_pgadmin_http() {
  local i code
  for i in $(seq 1 45); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PGADMIN_HOST_PORT}/login" 2>/dev/null || echo 000)"
    if [[ "${code}" == "200" || "${code}" == "302" ]]; then
      return 0
    fi
    # Container crashed?
    if ! docker_cmd ps --filter "name=larpsmp-pgadmin" --filter "status=running" --format '{{.Names}}' | grep -qx 'larpsmp-pgadmin'; then
      return 1
    fi
    sleep 2
  done
  return 2
}

probe_jdbc() {
  # Prefer docker exec SQL (always available once postgres is healthy).
  compose_cmd exec -T postgres \
    psql -U "${DB_USER}" -d "${DB_NAME}" -c 'SELECT 1' >/dev/null 2>&1
}

compose_up_once() {
  # Force recreate avoids stale container-ID dependency errors after reclaim.
  compose_cmd up -d --force-recreate --remove-orphans
}

compose_up_with_retries() {
  local attempt max_attempts=3
  local err_file
  err_file="$(mktemp)"
  for attempt in $(seq 1 "${max_attempts}"); do
    log_info "Starting Docker Compose stack (attempt ${attempt}/${max_attempts})..."
    if compose_up_once >"${err_file}" 2>&1; then
      rm -f "${err_file}"
      return 0
    fi
    log_warn "Compose up failed on attempt ${attempt}:"
    sed 's/^/    /' "${err_file}" >&2 || true
    log_info "Purging LarpSMP containers/networks and retrying..."
    compose_cmd down --remove-orphans >/dev/null 2>&1 || true
    remove_larpsmp_containers
    remove_larpsmp_networks
    sleep $((attempt + 1))
  done
  log_fail "Compose up output (last attempt):"
  sed 's/^/    /' "${err_file}" >&2 || true
  rm -f "${err_file}"
  return 1
}

phase_compose_stack() {
  log_phase "compose-stack"

  sync_pgadmin_secrets
  log_ok "Synced pgAdmin servers.json + pgpass from .env"

  if ! compose_up_with_retries; then
    die "Failed to start Postgres + pgAdmin after retries.
Check Docker with: docker compose --env-file .env -f docker/docker-compose.yml logs"
  fi

  log_info "Waiting for Postgres to become healthy..."
  if ! wait_for_postgres; then
    compose_cmd logs --tail=80 postgres || true
    # One recovery path: recreate postgres only, then wait again.
    log_warn "Postgres not healthy — recreating the stack once more..."
    compose_cmd down --remove-orphans >/dev/null 2>&1 || true
    remove_larpsmp_containers
    remove_larpsmp_networks
    sleep 2
    if ! compose_up_with_retries || ! wait_for_postgres; then
      compose_cmd logs --tail=80 postgres || true
      die "Postgres did not become ready in time."
    fi
  fi
  log_ok "Postgres is ready (container health)."

  if ! probe_jdbc; then
    die "Could not run SELECT 1 against ${DB_NAME} as ${DB_USER}."
  fi
  log_ok "Database accepts queries."

  local pgadmin_status=0
  wait_for_pgadmin_http || pgadmin_status=$?
  case "${pgadmin_status}" in
    0) log_ok "pgAdmin login UI responds on http://127.0.0.1:${PGADMIN_HOST_PORT}/login" ;;
    1)
      compose_cmd logs --tail=80 pgadmin || true
      log_warn "pgAdmin not running — recreating pgAdmin service..."
      compose_cmd up -d --force-recreate --no-deps pgadmin >/dev/null 2>&1 || true
      pgadmin_status=0
      wait_for_pgadmin_http || pgadmin_status=$?
      if [[ "${pgadmin_status}" -eq 1 ]]; then
        compose_cmd logs --tail=80 pgadmin || true
        die "pgAdmin container is not running."
      fi
      if [[ "${pgadmin_status}" -eq 0 ]]; then
        log_ok "pgAdmin login UI responds on http://127.0.0.1:${PGADMIN_HOST_PORT}/login"
      else
        log_warn "pgAdmin HTTP not ready yet; container is up — open http://localhost:${PGADMIN_HOST_PORT} shortly."
      fi
      ;;
    2) log_warn "pgAdmin HTTP not ready yet; container is up — open http://localhost:${PGADMIN_HOST_PORT} shortly." ;;
  esac
}

# ---------------------------------------------------------------------------
# Phase 4 — Plugin config sync
# ---------------------------------------------------------------------------

config_needs_repair() {
  local file="$1"
  [[ ! -f "${file}" ]] && return 0
  grep -q 'test-account:' "${file}" 2>/dev/null && return 0
  grep -qE 'jdbc-url:.*:5432/' "${file}" 2>/dev/null && return 0
  grep -q "jdbc-url:.*${POSTGRES_HOST_PORT}" "${file}" 2>/dev/null || return 0
  grep -q "username:.*${DB_USER}" "${file}" 2>/dev/null || return 0
  return 1
}

apply_env_to_runtime_config() {
  local file="$1"
  python3 - "${file}" "${JDBC_URL}" "${DB_USER}" "${DB_PASSWORD}" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
jdbc, user, password = sys.argv[2], sys.argv[3], sys.argv[4]
text = path.read_text(encoding="utf-8")

def replace_key(block: str, key: str, value: str) -> str:
    pattern = rf"(^[ \t]*{re.escape(key)}:[ \t]*).*$"
    repl = rf'\1"{value}"'
    updated, count = re.subn(pattern, repl, block, count=1, flags=re.M)
    if count == 0:
        raise SystemExit(f"Missing key {key} in {path}")
    return updated

# Limit replacements to the auth.database section when possible.
db_match = re.search(r"(?ms)^([ \t]*database:\n(?:[ \t]+.+\n)*)", text)
if db_match:
    start, end = db_match.span(1)
    section = db_match.group(1)
    section = replace_key(section, "jdbc-url", jdbc)
    section = replace_key(section, "username", user)
    section = replace_key(section, "password", password)
    text = text[:start] + section + text[end:]
else:
    text = replace_key(text, "jdbc-url", jdbc)
    text = replace_key(text, "username", user)
    text = replace_key(text, "password", password)

path.write_text(text, encoding="utf-8")
PY
}

phase_plugin_config() {
  log_phase "plugin-config"

  mkdir -p "${RUNTIME_CONFIG_DIR}"

  if [[ ! -f "${RUNTIME_CONFIG}" ]]; then
    cp "${SOURCE_CONFIG}" "${RUNTIME_CONFIG}"
    log_ok "Seeded ${RUNTIME_CONFIG} from source defaults."
  elif config_needs_repair "${RUNTIME_CONFIG}"; then
    if [[ ! -f "${RUNTIME_CONFIG}.bak" ]]; then
      cp "${RUNTIME_CONFIG}" "${RUNTIME_CONFIG}.bak"
      log_info "Backed up previous config to config.yml.bak"
    fi
    cp "${SOURCE_CONFIG}" "${RUNTIME_CONFIG}"
    log_ok "Repaired runtime config from source template."
  else
    log_ok "Runtime plugin config present."
  fi

  apply_env_to_runtime_config "${RUNTIME_CONFIG}"
  log_ok "Applied .env DB settings to runtime config (${JDBC_URL})."
}

# ---------------------------------------------------------------------------
# Phase 5 — Build, test, Paper prep
# ---------------------------------------------------------------------------

ensure_paper_jar() {
  mkdir -p "${DEV_SERVER_DIR}/plugins"

  find "${DEV_SERVER_DIR}" -maxdepth 1 -type f -name 'paper-*.jar' ! -name "paper-${PAPER_VERSION}-*.jar" -delete

  local paper_jar
  paper_jar="$(find "${DEV_SERVER_DIR}" -maxdepth 1 -type f -name "paper-${PAPER_VERSION}-*.jar" | sort | tail -n 1 || true)"
  if [[ -n "${paper_jar}" ]]; then
    echo "${paper_jar}"
    return 0
  fi

  log_info "Downloading Paper ${PAPER_VERSION}..."
  local build_json
  build_json="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${PAPER_VERSION}/builds")"
  local paper_meta
  readarray -t paper_meta < <(printf '%s' "${build_json}" | python3 -c '
import json, sys
builds = json.load(sys.stdin)
if not builds:
    raise SystemExit("No Paper builds found")
latest = builds[0]
download = latest["downloads"]["server:default"]
print(download["url"])
print(download["name"])
print(latest["id"])
')
  local download_url="${paper_meta[0]}"
  local paper_name="${paper_meta[1]}"
  local paper_build="${paper_meta[2]}"
  paper_jar="${DEV_SERVER_DIR}/${paper_name}"
  log_info "Fetching Paper ${PAPER_VERSION} build ${paper_build} (${paper_name})..."
  curl -fL --progress-bar -o "${paper_jar}" "${download_url}"
  log_ok "Downloaded ${paper_name}"
  echo "${paper_jar}"
}

ensure_server_files() {
  if [[ ! -f "${DEV_SERVER_DIR}/eula.txt" ]]; then
    if [[ -f "${DEV_SERVER_DIR}/eula.txt.template" ]]; then
      cp "${DEV_SERVER_DIR}/eula.txt.template" "${DEV_SERVER_DIR}/eula.txt"
    else
      printf 'eula=true\n' > "${DEV_SERVER_DIR}/eula.txt"
    fi
  fi

  if [[ ! -f "${DEV_SERVER_DIR}/server.properties" && -f "${DEV_SERVER_DIR}/server.properties.template" ]]; then
    cp "${DEV_SERVER_DIR}/server.properties.template" "${DEV_SERVER_DIR}/server.properties"
  fi

  if [[ -f "${DEV_SERVER_DIR}/server.properties" ]]; then
    sed -i 's/^online-mode=.*/online-mode=false/' "${DEV_SERVER_DIR}/server.properties"
    sed -i 's/^white-list=.*/white-list=false/' "${DEV_SERVER_DIR}/server.properties"
    sed -i 's/^enforce-whitelist=.*/enforce-whitelist=false/' "${DEV_SERVER_DIR}/server.properties"
    if grep -q '^enforce-secure-profile=' "${DEV_SERVER_DIR}/server.properties"; then
      sed -i 's/^enforce-secure-profile=.*/enforce-secure-profile=false/' "${DEV_SERVER_DIR}/server.properties"
    else
      printf '\nenforce-secure-profile=false\n' >> "${DEV_SERVER_DIR}/server.properties"
    fi
    grep -q '^white-list=' "${DEV_SERVER_DIR}/server.properties" || printf 'white-list=false\n' >> "${DEV_SERVER_DIR}/server.properties"
    grep -q '^enforce-whitelist=' "${DEV_SERVER_DIR}/server.properties" || printf 'enforce-whitelist=false\n' >> "${DEV_SERVER_DIR}/server.properties"
  fi
}

phase_build_and_paper() {
  log_phase "build-and-paper"

  cd "${ROOT_DIR}"
  log_info "Running ./gradlew build (compile + tests)..."
  ./gradlew build
  log_ok "Gradle build and tests passed."

  local paper_jar
  paper_jar="$(ensure_paper_jar)"
  ensure_server_files

  rm -f "${DEV_SERVER_DIR}/plugins"/money-event-*.jar
  local plugin_jar
  plugin_jar="$(ls -1t "${ROOT_DIR}/build/libs"/money-event-*.jar 2>/dev/null | head -n 1 || true)"
  [[ -n "${plugin_jar}" ]] || die "Plugin JAR not found in build/libs after build."
  cp "${plugin_jar}" "${DEV_SERVER_DIR}/plugins/"
  log_ok "Deployed $(basename "${plugin_jar}") to dev-server/plugins/"

  # Export for start phase
  PAPER_JAR_PATH="${paper_jar}"
}

# ---------------------------------------------------------------------------
# Phase 6 — Ready report + start
# ---------------------------------------------------------------------------

print_ready_report() {
  log_phase "ready"
  cat <<EOF

LarpSMP is ready
----------------
Java:      ${JAVA_HOME} ($(java_major_of "${JAVA_HOME}"))
Secrets:   ${ENV_FILE}
Postgres:  127.0.0.1:${POSTGRES_HOST_PORT}  (user/db: ${DB_USER} / ${DB_NAME})
pgAdmin:   http://localhost:${PGADMIN_HOST_PORT}
           login: ${PGADMIN_EMAIL} / ${PGADMIN_PASSWORD}
Minecraft: localhost:${PAPER_HOST_PORT}  (Paper ${PAPER_VERSION}, offline-mode)
JDBC:      ${JDBC_URL}

Join with a Minecraft ${PAPER_VERSION} client.
Use Sign up to create the first account (no seeded users).
Type 'stop' in the Paper console to shut down the game server.
Docker Postgres + pgAdmin keep running in the background.
Share/edit secrets in .env (template: .env.example).

EOF
}

start_paper() {
  log_phase "start-paper"
  cd "${DEV_SERVER_DIR}"
  log_info "Starting Paper ${PAPER_VERSION} (localhost:${PAPER_HOST_PORT}, offline-mode)..."
  # Disable ERR trap noise on intentional exec replacement.
  trap - ERR
  exec "${JAVA_HOME}/bin/java" -Xms1G -Xmx2G -jar "$(basename "${PAPER_JAR_PATH}")" --nogui
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

main() {
  phase_preflight
  phase_audit
  phase_remediate
  phase_reclaim
  phase_compose_stack
  phase_plugin_config
  phase_build_and_paper
  print_ready_report
  if [[ "${LARPSMP_BOOTSTRAP_SKIP_PAPER:-}" == "1" ]]; then
    log_info "LARPSMP_BOOTSTRAP_SKIP_PAPER=1 set — not starting Paper."
    exit 0
  fi
  start_paper
}

main "$@"
