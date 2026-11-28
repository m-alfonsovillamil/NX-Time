#!/usr/bin/env bash
# Prepara una versión de la app para publicarla: AAB para Google Play, APK
# para GitHub y mapping.txt, comprobados. NO publica nada: al final dice el
# comando, y publicar sigue siendo con permiso (docs/DESPLIEGUE.md).
#
# Uso:
#   scripts/dev/publicar-app.sh [--ref main] [--salida DIR] [--probar]
#
# Con --probar no exige que el versionCode suba: para ensayar el resto con
# una versión ya publicada.
#
# Qué hace, en orden, y se para en el primer fallo:
#   1. Compila en un worktree FUERA de OneDrive y sin espacios en la ruta
#      ($HOME/nxwt-apk): la carpeta «NX Time» rompe el release, y OneDrive y
#      VS Code bloquean build/ (ver la memoria de trampas del entorno).
#   2. Comprueba que el versionCode sube respecto a la última etiqueta app-v*.
#   3. bundleProdRelease y assembleProdRelease, firmados con la clave de
#      ~/.gradle/gradle.properties (si falta, se para: sin firmar no vale).
#   4. Que el certificado del APK y el del AAB son el de assetlinks.json: si
#      no, los botones de los correos dejan de abrir la app (App Links).
#   5. Que las librerías nativas valen para páginas de 16 KB (Play lo exige):
#      scripts/dev/comprobar-16kb.py.
#   6. Deja AAB, APK y mapping.txt juntos en $SALIDA/<versión>/ y escribe el
#      comando de la release de GitHub.

set -euo pipefail

REF=main
SALIDA="$HOME/nxtime-releases"
PROBAR=0
while [ $# -gt 0 ]; do
  case "$1" in
    --ref) REF="$2"; shift 2 ;;
    --salida) SALIDA="$2"; shift 2 ;;
    --probar) PROBAR=1; shift ;;
    *) echo "Opción desconocida: $1"; exit 2 ;;
  esac
done

RAIZ="$(git rev-parse --show-toplevel)"
WT="$HOME/nxwt-apk"
APP=nx-time-frontend-android
PY="python"
command -v python >/dev/null 2>&1 || PY="python3"
fallar() { echo "✗ $*"; exit 1; }

# --- 0. La firma y la URL de producción, fuera del repositorio ---
PROPS="$HOME/.gradle/gradle.properties"
for clave in nxtime.release.storeFile nxtime.prod.url; do
  grep -q "^$clave=" "$PROPS" 2>/dev/null || fallar "Falta $clave en $PROPS: el release saldría sin firmar o sin URL."
done

# --- 1. El worktree ---
SHA="$(git rev-parse "$REF")"
if [ -d "$WT" ]; then
  git -C "$WT" checkout -q --detach "$SHA"
else
  git worktree add -q --detach "$WT" "$SHA"
fi
[ -f "$WT/local.properties" ] || cp "$RAIZ/local.properties" "$WT/" 2>/dev/null \
  || cp "$HOME/nxwt-o/local.properties" "$WT/" 2>/dev/null \
  || fallar "No encuentro local.properties para copiarlo a $WT."
echo "· Compilando $REF (${SHA:0:7}) en $WT"

# --- 2. El versionCode sube ---
GRADLE_APP="$WT/$APP/build.gradle.kts"
CODIGO="$(grep -oE 'versionCode = [0-9]+' "$GRADLE_APP" | grep -oE '[0-9]+')"
NOMBRE="$(grep -oE 'versionName = "[^"]+"' "$GRADLE_APP" | cut -d'"' -f2)"
ULTIMA="$(git tag -l 'app-v*' --sort=-v:refname | head -1)"
if [ -n "$ULTIMA" ]; then
  ANTERIOR="$(git show "$ULTIMA:$APP/build.gradle.kts" | grep -oE 'versionCode = [0-9]+' | grep -oE '[0-9]+')"
  if [ "$CODIGO" -le "$ANTERIOR" ]; then
    [ "$PROBAR" = 1 ] || fallar "versionCode $CODIGO no sube respecto a $ULTIMA ($ANTERIOR)."
    echo "· (--probar) versionCode $CODIGO no sube respecto a $ULTIMA: sigo igual"
  fi
  echo "· Versión $NOMBRE ($CODIGO), después de $ULTIMA ($ANTERIOR)"
fi

# --- 3. Compilar ---
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21'; then
  for candidato in "$HOME"/.gradle/jdks/eclipse_adoptium-21-*; do
    [ -d "$candidato" ] && export JAVA_HOME="$candidato" && break
  done
fi
(cd "$WT" && ./gradlew -q ":$APP:bundleProdRelease" ":$APP:assembleProdRelease") \
  || fallar "La compilación ha fallado (si es «Unable to delete directory»: ./gradlew --stop y repetir)."
APK="$WT/$APP/build/outputs/apk/prod/release/$APP-prod-release.apk"
AAB="$WT/$APP/build/outputs/bundle/prodRelease/$APP-prod-release.aab"
MAPPING="$WT/$APP/build/outputs/mapping/prodRelease/mapping.txt"
for f in "$APK" "$AAB" "$MAPPING"; do [ -f "$f" ] || fallar "No se ha generado $f"; done

# --- 4. La huella, la de assetlinks.json ---
# sdk.dir va escapado como en un .properties de Java: C\:\\Users\\...
SDK="$("$PY" - "$WT/local.properties" <<'EOF'
import re, sys
valor = re.search(r"^sdk\.dir=(.*)$", open(sys.argv[1], encoding="utf-8").read(), re.M)[1].strip()
print(valor.replace("\\:", ":").replace("\\\\", "/"))
EOF
)"
HERRAMIENTAS="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)"
APKSIGNER="${HERRAMIENTAS}apksigner"
[ -f "$APKSIGNER.bat" ] && APKSIGNER="$APKSIGNER.bat"
# Las salidas enteras primero y luego se filtran: con pipefail, un `grep -m1`
# que cierra la tubería antes de tiempo tumba el script sin decir nada. Y el
# keytool del JDK 21 y en inglés: el de otro JDK en español revienta al
# imprimir (MissingFormatArgumentException).
CERT_APK="$("$APKSIGNER" verify --print-certs "$APK")" || fallar "apksigner no da por bueno el APK."
CERT_AAB="$("$JAVA_HOME/bin/keytool" -J-Duser.language=en -printcert -jarfile "$AAB")" || fallar "keytool no lee la firma del AAB."
HUELLA_APK="$(awk '/SHA-256 digest/ {print $NF; exit}' <<< "$CERT_APK")"
HUELLA_AAB="$(awk '/SHA256:/ {print $NF; exit}' <<< "$CERT_AAB")"
[ -n "$HUELLA_APK" ] && [ -n "$HUELLA_AAB" ] || fallar "No encuentro la huella SHA-256 del APK o del AAB."
"$PY" - "$RAIZ/nx-time-frontend-web/public/.well-known/assetlinks.json" "$HUELLA_APK" "$HUELLA_AAB" <<'EOF' || fallar "La firma no es la de assetlinks.json."
import json, sys
sys.stdout.reconfigure(encoding="utf-8")
publicadas = {h.upper() for e in json.load(open(sys.argv[1], encoding="utf-8")) for h in e["target"]["sha256_cert_fingerprints"]}
apk = ":".join(sys.argv[2][i:i + 2] for i in range(0, len(sys.argv[2]), 2)).upper() if ":" not in sys.argv[2] else sys.argv[2].upper()
aab = sys.argv[3].upper()
for nombre, huella in (("APK", apk), ("AAB", aab)):
    print(f"· {nombre} firmado con {huella[:23]}…", "(la de assetlinks.json)" if huella in publicadas else "(¡NO es la de assetlinks.json!)")
sys.exit(0 if apk in publicadas and aab in publicadas else 1)
EOF

# --- 5. 16 KB ---
"$PY" "$RAIZ/scripts/dev/comprobar-16kb.py" "$APK" "$AAB" || fallar "Hay librerías nativas que no valen para 16 KB."

# --- 6. Juntarlo todo ---
DESCARGA="$(grep -oE 'download-url: "[^"]*"' "$WT/nx-time-backend/src/main/resources/application.yml" | grep -oE '[^/]+\.apk' || true)"
DESCARGA="${DESCARGA:-app.apk}"
DESTINO="$SALIDA/$NOMBRE"
mkdir -p "$DESTINO"
cp "$APK" "$DESTINO/$DESCARGA"
cp "$AAB" "$DESTINO/${DESCARGA%.apk}.aab"
cp "$MAPPING" "$DESTINO/mapping.txt"
echo
echo "✓ Listo en $DESTINO: $DESCARGA, ${DESCARGA%.apk}.aab y mapping.txt (guárdalo: sin él, los fallos de Sentry salen ofuscados)."
echo
echo "Para publicar (CON PERMISO):"
echo "  GitHub: gh release create app-v$NOMBRE \"$DESTINO/$DESCARGA\" --target $SHA --title \"$NOMBRE (Android)\""
echo "  Play:   subir ${DESCARGA%.apk}.aab en Play Console (y mapping.txt como archivo de desofuscación)."
