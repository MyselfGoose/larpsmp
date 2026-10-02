#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_SERVER_DIR="${ROOT_DIR}/dev-server"
PAPER_VERSION="26.3"
PROJECT_JDK="${ROOT_DIR}/tools/jdk-25"
SYSTEM_JDK="/usr/lib/jvm/java-25-openjdk"

resolve_java() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    echo "${JAVA_HOME}"
    return 0
  fi
  if [[ -x "${SYSTEM_JDK}/bin/java" ]]; then
    echo "${SYSTEM_JDK}"
    return 0
  fi
  if [[ -x "${PROJECT_JDK}/bin/java" ]]; then
    echo "${PROJECT_JDK}"
    return 0
  fi
  return 1
}

if ! JAVA_HOME_RESOLVED="$(resolve_java)"; then
  cat <<'EOF' >&2
Java 25 is required to build and run Paper 26.3.

Install a system JDK:
  sudo pacman -S --needed jdk25-openjdk

Or place a JDK 25 under:
  tools/jdk-25
EOF
  exit 1
fi

export JAVA_HOME="${JAVA_HOME_RESOLVED}"
export PATH="${JAVA_HOME}/bin:${PATH}"

JAVA_MAJOR="$("${JAVA_HOME}/bin/java" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2; exit}' | tr -d '[:space:]')"
if [[ "${JAVA_MAJOR}" != "25" ]]; then
  echo "Expected Java 25, found Java ${JAVA_MAJOR} at ${JAVA_HOME}" >&2
  exit 1
fi

echo "Using Java ${JAVA_MAJOR} from ${JAVA_HOME}"

cd "${ROOT_DIR}"
./gradlew build

mkdir -p "${DEV_SERVER_DIR}/plugins"

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
echo "Starting Paper development server (localhost:25565). Type 'stop' to shut down."
exec "${JAVA_HOME}/bin/java" -Xms1G -Xmx2G -jar "$(basename "${PAPER_JAR}")" --nogui
