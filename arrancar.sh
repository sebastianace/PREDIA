#!/usr/bin/env bash
# Levanta Predia completo para probarlo: PostgreSQL + Mailpit (Docker), el backend y el frontend.
# Uso: ./arrancar.sh        (Ctrl+C apaga el backend y el frontend; Docker queda encendido)
#      ./arrancar.sh --docker-off   (al salir, también detiene PostgreSQL y Mailpit)
set -uo pipefail

cd "$(dirname "$0")"

AZUL=$'\033[1;34m'; ROJO=$'\033[1;31m'; VERDE=$'\033[1;32m'; SIN=$'\033[0m'
info() { echo "${AZUL}▸${SIN} $*"; }
fallo() { echo "${ROJO}✗${SIN} $*" >&2; exit 1; }

APAGAR_DOCKER=false
[[ "${1:-}" == "--docker-off" ]] && APAGAR_DOCKER=true

# --- Requisitos ---------------------------------------------------------------
[[ -f .env ]] || fallo "Falta el archivo .env. Créalo con: cp .env.example .env (y pon un JWT_SECRET de 32+ caracteres)."

command -v brew >/dev/null || fallo "No encuentro Homebrew."
JDK21="$(brew --prefix openjdk@21 2>/dev/null)/libexec/openjdk.jdk/Contents/Home"
[[ -d "$JDK21" ]] || fallo "Falta Java 21. Instálalo con: brew install openjdk@21"
export JAVA_HOME="$JDK21"
export TESSDATA_PREFIX="${TESSDATA_PREFIX:-/opt/homebrew/share/tessdata}"

docker info >/dev/null 2>&1 || fallo "Docker no está corriendo. Abre Docker Desktop y vuelve a intentar."

for puerto in 8080 5173; do
  if lsof -iTCP:"$puerto" -sTCP:LISTEN >/dev/null 2>&1; then
    fallo "El puerto $puerto ya está ocupado (¿Predia ya está corriendo?). Ciérralo e intenta de nuevo."
  fi
done

mkdir -p logs

# --- Apagado limpio -----------------------------------------------------------
PIDS=()
matar_arbol() {
  local pid=$1 hijo
  for hijo in $(pgrep -P "$pid" 2>/dev/null); do matar_arbol "$hijo"; done
  kill "$pid" 2>/dev/null
}
apagar() {
  local codigo=${1:-0}
  trap - INT TERM EXIT
  echo
  info "Apagando backend y frontend..."
  for pid in "${PIDS[@]:-}"; do [[ -n "$pid" ]] && matar_arbol "$pid"; done
  if $APAGAR_DOCKER; then
    info "Deteniendo PostgreSQL y Mailpit..."
    docker compose stop >/dev/null 2>&1
  fi
  exit "$codigo"
}
trap 'apagar 0' INT TERM
trap 'apagar $?' EXIT

# --- Base de datos y correo ---------------------------------------------------
info "Levantando PostgreSQL y Mailpit..."
docker compose up -d --wait >/dev/null 2>&1 || fallo "No se pudo levantar Docker Compose. Mira: docker compose logs"

# --- Backend ------------------------------------------------------------------
info "Arrancando el backend (Flyway migra y se siembra la demo)..."
./mvnw spring-boot:run >logs/backend.log 2>&1 &
BACKEND=$!
PIDS+=("$BACKEND")

# --- Frontend -----------------------------------------------------------------
if [[ ! -d frontend/node_modules ]]; then
  info "Instalando dependencias del frontend (solo la primera vez)..."
  (cd frontend && npm install >../logs/npm-install.log 2>&1) || fallo "Falló npm install. Mira logs/npm-install.log"
fi
info "Arrancando el frontend..."
(cd frontend && exec npm run dev >../logs/frontend.log 2>&1) &
FRONTEND=$!
PIDS+=("$FRONTEND")

# --- Esperar a que respondan --------------------------------------------------
esperar() { # $1 = nombre, $2 = url, $3 = pid, $4 = log, $5 = intentos
  local i
  for ((i = 0; i < $5; i++)); do
    if curl -s -o /dev/null "$2"; then return 0; fi
    if ! kill -0 "$3" 2>/dev/null; then
      echo "${ROJO}✗${SIN} $1 se detuvo. Últimas líneas de $4:" >&2
      tail -n 20 "$4" >&2
      exit 1
    fi
    sleep 2
  done
  fallo "$1 no respondió a tiempo. Revisa $4"
}
info "Esperando al backend (puede tardar 20-40 s)..."
esperar "El backend" http://localhost:8080/swagger-ui.html "$BACKEND" logs/backend.log 90
esperar "El frontend" http://localhost:5173 "$FRONTEND" logs/frontend.log 30

# Proveedor de IA: la variable del entorno manda sobre el .env (igual que en la aplicación).
valor_env() { grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2-; }
PROVEEDOR="${PREDIA_IA_PROVEEDOR:-$(valor_env PREDIA_IA_PROVEEDOR)}"
if [[ "${PROVEEDOR:-openai}" == "ollama" ]]; then
  OLLAMA="${OLLAMA_URL:-$(valor_env OLLAMA_URL)}"; OLLAMA="${OLLAMA:-http://localhost:11434}"
  if curl -s -m 3 "$OLLAMA" >/dev/null 2>&1; then
    IA="con IA local (Ollama, ${OLLAMA_CHAT_MODEL:-$(valor_env OLLAMA_CHAT_MODEL)})"
    [[ "$IA" == *", )" ]] && IA="con IA local (Ollama)"
  else
    IA="${ROJO}con Ollama, pero NO RESPONDE en $OLLAMA${SIN} — ejecuta en otra terminal: ollama serve"
  fi
elif grep -qE '^OPENAI_API_KEY=.+' .env; then
  IA="con IA (OPENAI_API_KEY configurada)"
else
  IA="en modo manual (sin OPENAI_API_KEY)"
fi

cat <<EOF

${VERDE}Predia está listo${SIN} — $IA

  App        http://localhost:5173
  API        http://localhost:8080   (Swagger: /swagger-ui.html)
  Correos    http://localhost:8026   (Mailpit)

  Usuarios   laura@altos.test (ASESOR) · admin@altos.test (ADMIN)
             contraseña: DEMO_PASSWORD de tu .env

  Registros  logs/backend.log · logs/frontend.log
  Ctrl+C para apagar.
EOF

wait
