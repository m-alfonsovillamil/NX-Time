#!/usr/bin/env bash
# Levanta el backend (perfiles dev,demo) sobre una base desechable del
# Postgres de Docker, espera a que estén los datos de demo y deja el log en
# un fichero. Así no se pisa la base `nxtime`, que arrastra las migraciones
# de otras ramas.
#
# Uso:
#   scripts/dev/backend-desechable.sh <base> [puerto] [--sin-compilar]
#   scripts/dev/backend-desechable.sh --parar [puerto]
#   scripts/dev/backend-desechable.sh --borrar <base>
#
# Necesita Docker Desktop arrancado y el contenedor `nxtime-postgres`
# (`docker compose up -d postgres`).
set -euo pipefail

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
JDK21="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2"
[ -d "$JDK21" ] && export JAVA_HOME="$JDK21"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

# En esta máquina `netstat` no siempre enseña el puerto del backend, así que
# el puerto se da por ocupado si algo responde en él, y el proceso se busca
# por su línea de comandos (`--server.port=N`).
puerto_ocupado() {
  [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:$1/" || true)" != "000" ]
}

pid_del_puerto() {
  powershell.exe -NoProfile -Command \
    "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match '--server.port=$1(\\s|\$)' } | ForEach-Object { \$_.ProcessId }" \
    | tr -d '\r' | head -1
}

psql_admin() {
  docker exec nxtime-postgres psql -U nxtime -d postgres -v ON_ERROR_STOP=1 -qtAc "$1"
}

case "${1:-}" in
  ""|-h|--help)
    sed -n '2,13p' "$0"; exit 0 ;;
  --parar)
    PUERTO=${2:-8080}
    PID=$(pid_del_puerto "$PUERTO")
    if [ -z "$PID" ]; then
      if puerto_ocupado "$PUERTO"; then
        echo "Algo responde en el $PUERTO, pero no es un backend lanzado con --server.port=$PUERTO. Búscalo a mano."
        exit 1
      fi
      echo "No hay nada en el $PUERTO."
      exit 0
    fi
    taskkill //F //PID "$PID" > /dev/null && echo "Parado el PID $PID (puerto $PUERTO)."
    exit 0 ;;
  --borrar)
    BASE=${2:?Falta el nombre de la base}
    [ "$BASE" != "nxtime" ] || { echo "La base nxtime no se borra con esto."; exit 1; }
    psql_admin "DROP DATABASE IF EXISTS $BASE WITH (FORCE)"
    echo "Base $BASE borrada."
    exit 0 ;;
esac

BASE=$1
PUERTO=${2:-8080}
[[ "$BASE" =~ ^[a-z][a-z0-9_]*$ ]] || { echo "Nombre de base no válido: $BASE"; exit 1; }
[ "$BASE" != "nxtime" ] || { echo "Usa una base distinta de nxtime."; exit 1; }

# 1. El puerto tiene que estar libre: una instancia vieja seguiría
#    respondiendo contra otra base y las pruebas engañarían.
if puerto_ocupado "$PUERTO"; then
  echo "El puerto $PUERTO está ocupado (PID ${PID_VIEJO:=$(pid_del_puerto "$PUERTO")}). Páralo antes:"
  echo "  $0 --parar $PUERTO"
  exit 1
fi

# 2. La base: se crea si no existe; si existe, se reutiliza.
if [ "$(psql_admin "SELECT 1 FROM pg_database WHERE datname = '$BASE'")" != "1" ]; then
  psql_admin "CREATE DATABASE $BASE OWNER nxtime"
  echo "Base $BASE creada."
fi
psql_admin "GRANT CONNECT ON DATABASE $BASE TO nxtime_app"

# 3. El jar. En Windows, Gradle falla a menudo con «Unable to delete
#    directory» porque algún proceso sujeta build/: se para el demonio, se
#    borra la carpeta que diga el error y se repite.
compilar() {
  local salida carpeta
  for intento in 1 2 3 4 5 6; do
    if salida=$(cd "$RAIZ" && ./gradlew -q :nx-time-backend:bootJar 2>&1); then
      return 0
    fi
    carpeta=$(printf '%s\n' "$salida" | sed -n "s/.*Unable to delete directory '\([^']*\)'.*/\1/p" | head -1)
    if [ -z "$carpeta" ]; then
      printf '%s\n' "$salida" | grep -v '^\s*$' | head -30
      return 1
    fi
    echo "Intento $intento: build/ bloqueado; borro $(basename "$carpeta") y repito."
    (cd "$RAIZ" && ./gradlew -q --stop > /dev/null 2>&1 || true)
    rm -rf "$(cygpath -u "$carpeta" 2> /dev/null || echo "$carpeta")" 2> /dev/null || true
  done
  echo "Gradle sigue bloqueado tras 6 intentos."
  return 1
}
if [ "${3:-}" != "--sin-compilar" ]; then
  compilar
fi
JAR=$(ls "$RAIZ"/nx-time-backend/build/libs/*.jar | grep -v -- '-plain' | head -1)

# 4. Arrancar, sin el SQL de show-sql (llena el log y no aporta).
LOG="$RAIZ/nx-time-backend/build/backend-$BASE.log"
URL="jdbc:postgresql://localhost:5433/$BASE"
nohup "$JAVA" -jar "$JAR" \
  --spring.profiles.active=dev,demo \
  --server.port="$PUERTO" \
  --spring.datasource.url="$URL" \
  --spring.flyway.url="$URL" \
  --spring.jpa.show-sql=false \
  > "$LOG" 2>&1 &
PID=$!

# 5. Esperar a los datos de demo (no basta /actuator/health: el sembrador
#    sigue trabajando cuando el servidor ya responde).
for _ in $(seq 1 150); do
  if grep -q "Datos de demo \(listos\|ya presentes\)" "$LOG"; then
    echo "Listo: http://localhost:$PUERTO (base $BASE, log en $LOG)."
    echo "Para pararlo: $0 --parar $PUERTO"
    exit 0
  fi
  if ! kill -0 "$PID" 2> /dev/null; then
    echo "El backend se ha parado al arrancar. Errores del log:"
    grep -E "ERROR|Exception|already in use" "$LOG" | head -20
    exit 1
  fi
  sleep 2
done
echo "No ha arrancado en 5 minutos. Final del log ($LOG):"
tail -20 "$LOG"
exit 1
