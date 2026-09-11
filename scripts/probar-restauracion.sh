#!/usr/bin/env bash
#
# Restaura una copia de NX Time en un PostgreSQL desechable y comprueba que
# sirve (paso 4 del piloto, 09/2026).
#
# Una copia que nunca se ha restaurado no es una copia: es una carpeta. Este
# script es la prueba, y está pensado para repetirse, no para correr una vez.
#
#   scripts/probar-restauracion.sh                  # copia NUEVA de producción
#   scripts/probar-restauracion.sh copia.dump       # una copia ya hecha
#
# Sin argumento hace un pg_dump de producción en el momento (lee
# DATABASE_URL_UNPOOLED de .env.local) y además compara las filas de cada
# tabla con las de producción. Con un fichero, restaura ese.
#
# Levanta un postgres:18-alpine propio en el puerto 5434 y lo borra al
# terminar: NO toca la base de desarrollo del 5433.
#
# Comprueba:
#   1. que pg_restore termina sin errores
#   2. las mismas filas por tabla que producción (solo sin argumento)
#   3. las migraciones de Flyway, todas con éxito
#   4. que ninguna secuencia va por detrás de su id máximo: si lo hiciera,
#      el primer INSERT tras restaurar chocaría con una fila existente
#   5. que la auditoría sigue siendo append-only: los dos triggers de V5
#      disparan de verdad y nxtime_app sigue sin UPDATE ni DELETE (V3)
#
# Sale con 0 solo si todo pasa. Una consulta que falla cuenta como FALLO,
# nunca como "no he encontrado nada": la primera versión de este script dio
# por buenas las secuencias porque su consulta había muerto por la
# codificación y la salida vacía parecía una lista vacía.
#
# Necesita Docker y el cliente de PostgreSQL 18 (pg_restore no puede leer una
# copia de un pg_dump más nuevo que él).

set -o pipefail

PG_BIN="${PG_BIN:-/c/Program Files/PostgreSQL/18/bin}"
PUERTO="${PUERTO:-5434}"
CONTENEDOR=nxtime-prueba-restauracion
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
fallos=0

# psql en Windows toma la codificación de la consola y manda las tildes de
# las consultas como Latin-1 declaradas como UTF-8: "invalid byte sequence".
export PGCLIENTENCODING=UTF8

trap 'docker rm -f "$CONTENEDOR" >/dev/null 2>&1; rm -rf "$TMP"' EXIT

ok()    { echo "OK     $*"; }
fallo() { echo "FALLO  $*"; fallos=$((fallos + 1)); }

# Filas de cada tabla de public, una por línea ("tabla=filas"), en una sola
# consulta y sin tablas temporales: vale igual contra producción, que no se
# toca, que contra la base restaurada.
SQL_FILAS="SELECT table_name || '=' || (xpath('/row/n/text()', query_to_xml(format('SELECT count(*) AS n FROM public.%I', table_name), false, true, '')))[1]::text FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY 1"

consultar() { "$PG_BIN/psql" "$1" -X -A -t -q -v ON_ERROR_STOP=1 -c "$2"; }

# --- La copia -------------------------------------------------------------
DUMP="${1:-}"
COMPARAR=false
if [ -z "$DUMP" ]; then
    # .env.local lleva BOM y la primera línea da "command not found": es
    # inofensivo, pero no debe ensuciar la salida.
    set -a; . "$RAIZ/.env.local" 2>/dev/null; set +a
    [ -n "${DATABASE_URL_UNPOOLED:-}" ] || { echo "Falta DATABASE_URL_UNPOOLED en .env.local"; exit 2; }
    DUMP="$TMP/produccion.dump"
    echo "Copiando producción..."
    "$PG_BIN/pg_dump" "$DATABASE_URL_UNPOOLED" --format=custom --file="$DUMP" || { echo "pg_dump ha fallado"; exit 2; }
    # Justo después de la copia. Si alguien escribe en medio, saldrá como
    # diferencia: en el piloto no hay tráfico concurrente que lo explique.
    consultar "$DATABASE_URL_UNPOOLED" "$SQL_FILAS" > "$TMP/filas-produccion.txt" \
        || { echo "No se pudieron contar las filas de producción"; exit 2; }
    COMPARAR=true
fi
[ -f "$DUMP" ] || { echo "No existe $DUMP"; exit 2; }
echo "Copia: $DUMP ($(wc -c < "$DUMP") bytes)"

# --- PostgreSQL desechable -----------------------------------------------
docker rm -f "$CONTENEDOR" >/dev/null 2>&1
docker run -d --rm --name "$CONTENEDOR" -e POSTGRES_PASSWORD=prueba \
    -p "127.0.0.1:$PUERTO:5432" postgres:18-alpine >/dev/null || { echo "No se pudo arrancar Docker"; exit 2; }

# Por TCP y no por el socket: durante la inicialización, la imagen levanta un
# servidor temporal que solo escucha en el socket, y pg_isready diría "listo"
# justo antes de que se reinicie.
for _ in $(seq 60); do
    docker exec "$CONTENEDOR" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1 && break
    sleep 1
done

ADMIN="postgresql://postgres:prueba@127.0.0.1:$PUERTO/postgres"
DESTINO="postgresql://postgres:prueba@127.0.0.1:$PUERTO/neondb"

# Los roles que la copia nombra. En Neon ya existen; aquí hay que crearlos:
#  - neondb_owner, nxtime_app: propietario y GRANTs de V3. Sin ellos la
#    comprobación 5 no probaría nada.
#  - neon_auth, neon_superuser, cloud_admin: roles internos de Neon (el último
#    es dueño de unos DEFAULT PRIVILEGES). No afectan a los datos, pero sin
#    ellos pg_restore da errores, y un error esperado taparía uno que no lo es.
consultar "$ADMIN" "CREATE ROLE neondb_owner; CREATE ROLE nxtime_app; CREATE ROLE neon_auth; CREATE ROLE neon_superuser; CREATE ROLE cloud_admin;" \
    || { echo "No se pudieron crear los roles"; exit 2; }
consultar "$ADMIN" "CREATE DATABASE neondb OWNER neondb_owner;" || exit 2

# --- 1. Restaurar ---------------------------------------------------------
if "$PG_BIN/pg_restore" --dbname="$DESTINO" "$DUMP" 2> "$TMP/restore.err"; then
    ok "pg_restore sin errores"
else
    fallo "pg_restore ha dado errores:"
    grep -v '^\s*$' "$TMP/restore.err" | sed 's/^/         /' | head -20
fi

# --- 2. Filas por tabla ---------------------------------------------------
if ! consultar "$DESTINO" "$SQL_FILAS" > "$TMP/filas-restauradas.txt"; then
    fallo "no se pudieron contar las filas restauradas"
elif $COMPARAR; then
    if diff "$TMP/filas-produccion.txt" "$TMP/filas-restauradas.txt" > "$TMP/filas.diff"; then
        ok "mismas filas que producción en las $(wc -l < "$TMP/filas-produccion.txt") tablas"
    else
        fallo "las filas no coinciden con producción (< producción, > restaurada):"
        grep '^[<>]' "$TMP/filas.diff" | sed 's/^/         /'
    fi
else
    echo "       (filas restauradas: $(paste -sd' ' "$TMP/filas-restauradas.txt"))"
fi

# --- 3. Migraciones -------------------------------------------------------
if migraciones=$(consultar "$DESTINO" "SELECT count(*) FILTER (WHERE success) || '/' || count(*) FROM flyway_schema_history") \
    && [ -n "$migraciones" ] && [ "${migraciones%/*}" = "${migraciones#*/}" ]; then
    ok "migraciones de Flyway: $migraciones con éxito"
else
    fallo "migraciones de Flyway: '${migraciones:-la consulta falló}'"
fi

# --- 4. Secuencias --------------------------------------------------------
if atrasadas=$(consultar "$DESTINO" "
    SELECT x.tabla || '.' || x.columna || ' (secuencia en ' || coalesce(ps.last_value, 0) || ', maximo ' || mx.m || ')'
    FROM (
        SELECT c.table_name AS tabla, c.column_name AS columna,
               pg_get_serial_sequence(format('public.%I', c.table_name), c.column_name) AS secuencia
        FROM information_schema.columns c
        WHERE c.table_schema = 'public' AND (c.column_default LIKE 'nextval(%' OR c.is_identity = 'YES')
    ) x
    JOIN pg_sequences ps ON format('%I.%I', ps.schemaname, ps.sequencename)::regclass = x.secuencia::regclass
    CROSS JOIN LATERAL (
        SELECT (xpath('/row/m/text()', query_to_xml(format('SELECT max(%I) AS m FROM public.%I', x.columna, x.tabla), false, true, '')))[1]::text::bigint AS m
    ) mx
    WHERE mx.m IS NOT NULL AND coalesce(ps.last_value, 0) < mx.m"); then
    [ -z "$atrasadas" ] && ok "ninguna secuencia por detrás de su id máximo" \
        || fallo "secuencias atrasadas: $atrasadas"
else
    fallo "no se pudieron comprobar las secuencias (la consulta falló)"
fi

# --- 5. Auditoría append-only ---------------------------------------------
triggers=$(consultar "$DESTINO" "SELECT count(*) FROM pg_trigger WHERE tgrelid = 'public.auditoria_fichaje'::regclass AND NOT tgisinternal")
[ "$triggers" = "2" ] && ok "los 2 triggers de V5 existen" || fallo "triggers en auditoria_fichaje: '$triggers' (se esperaban 2)"

permisos=$(consultar "$DESTINO" "SELECT string_agg(p || '=' || has_table_privilege('nxtime_app', 'public.auditoria_fichaje', p), ' ' ORDER BY p) FROM unnest(ARRAY['DELETE','INSERT','SELECT','UPDATE']) p")
[ "$permisos" = "DELETE=false INSERT=true SELECT=true UPDATE=false" ] \
    && ok "nxtime_app puede leer e insertar en la auditoría, pero no modificarla ni borrarla" \
    || fallo "permisos de nxtime_app sobre la auditoría: '$permisos'"

# Que el trigger EXISTA no prueba que dispare: se intenta de verdad, como
# superusuario (que se salta los GRANT pero no los triggers), y se exige que
# la base lo impida. Un DELETE sobre una tabla vacía no dispara un trigger
# FOR EACH ROW, así que sin filas esa parte no demostraría nada.
filas_auditoria=$(consultar "$DESTINO" "SELECT count(*) FROM auditoria_fichaje")
for sentencia in "DELETE FROM auditoria_fichaje" "TRUNCATE auditoria_fichaje"; do
    if [ "$sentencia" = "DELETE FROM auditoria_fichaje" ] && [ "$filas_auditoria" = "0" ]; then
        echo "       (DELETE sin comprobar: la auditoría está vacía y el trigger de fila no dispararía)"
        continue
    fi
    if salida=$("$PG_BIN/psql" "$DESTINO" -X -q -v ON_ERROR_STOP=1 -c "BEGIN; $sentencia; ROLLBACK;" 2>&1); then
        fallo "la base PERMITIÓ \"$sentencia\" sobre la auditoría"
    elif echo "$salida" | grep -qi "auditor"; then
        ok "la base impide \"$sentencia\" sobre la auditoría"
    else
        fallo "\"$sentencia\" falló, pero no por el trigger: $salida"
    fi
done

echo
if [ "$fallos" -eq 0 ]; then
    echo "RESTAURACIÓN VERIFICADA"
else
    echo "RESTAURACIÓN CON $fallos FALLO(S)"
fi
exit "$fallos"
