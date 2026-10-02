#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_SERVER_DIR="${ROOT_DIR}/dev-server"
PAPER_VERSION="1.21.11"
MIN_JAVA_MAJOR=21
PROJECT_JDK="${ROOT_DIR}/tools/jdk-25"

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

if ! JAVA_HOME_RESOLVED="$(resolve_java)"; then
  cat <<EOF >&2
Java ${MIN_JAVA_MAJOR}+ is required to build and run Paper ${PAPER_VERSION}.

Install a system JDK (example on Arch/CachyOS):
  sudo pacman -S --needed jdk21-openjdk

Or place a compatible JDK under:
  tools/jdk-25
EOF
  exit 1
fi

export JAVA_HOME="${JAVA_HOME_RESOLVED}"
export PATH="${JAVA_HOME}/bin:${PATH}"

JAVA_MAJOR="$("${JAVA_HOME}/bin/java" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2; exit}' | tr -d '[:space:]')"
if [[ "${JAVA_MAJOR}" -lt "${MIN_JAVA_MAJOR}" ]]; then
  echo "Expected Java ${MIN_JAVA_MAJOR}+, found Java ${JAVA_MAJOR} at ${JAVA_HOME}" >&2
  exit 1
fi

echo "Using Java ${JAVA_MAJOR} from ${JAVA_HOME}"

cd "${ROOT_DIR}"
./gradlew build

mkdir -p "${DEV_SERVER_DIR}/plugins"

# Drop incompatible Paper jars from other Minecraft versions.
find "${DEV_SERVER_DIR}" -maxdepth 1 -type f -name 'paper-*.jar' ! -name "paper-${PAPER_VERSION}-*.jar" -delete

PAPER_JAR="$(find "${DEV_SERVER_DIR}" -maxdepth 1 -type f -name "paper-${PAPER_VERSION}-*.jar" | sort | tail -n 1 || true)"
if [[ -z "${PAPER_JAR}" ]]; then
  echo "Downloading Paper ${PAPER_VERSION}..."
  BUILD_JSON="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${PAPER_VERSION}/builds")"
  readarray -t PAPER_META < <(printf '%s' "${BUILD_JSON}" | python3 -c '
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
  DOWNLOAD_URL="${PAPER_META[0]}"
  PAPER_NAME="${PAPER_META[1]}"
  PAPER_BUILD="${PAPER_META[2]}"
  PAPER_JAR="${DEV_SERVER_DIR}/${PAPER_NAME}"
  echo "Fetching Paper ${PAPER_VERSION} build ${PAPER_BUILD} (${PAPER_NAME})..."
  curl -fL --progress-bar -o "${PAPER_JAR}" "${DOWNLOAD_URL}"
  echo "Downloaded ${PAPER_NAME}"
fi

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

# Keep offline/cracked joins enabled even if Paper regenerated server.properties.
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

# Remove previous builds of this plugin, then copy the newest shaded/plain jar.
rm -f "${DEV_SERVER_DIR}/plugins"/money-event-*.jar
PLUGIN_JAR="$(ls -1t "${ROOT_DIR}/build/libs"/money-event-*.jar 2>/dev/null | head -n 1 || true)"
if [[ -z "${PLUGIN_JAR}" ]]; then
  echo "Plugin JAR not found in build/libs" >&2
  exit 1
fi
cp "${PLUGIN_JAR}" "${DEV_SERVER_DIR}/plugins/"
echo "Deployed $(basename "${PLUGIN_JAR}") to dev-server/plugins/"

cd "${DEV_SERVER_DIR}"
echo "Starting Paper ${PAPER_VERSION} development server (localhost:25565, offline-mode). Type 'stop' to shut down."
exec "${JAVA_HOME}/bin/java" -Xms1G -Xmx2G -jar "$(basename "${PAPER_JAR}")" --nogui
