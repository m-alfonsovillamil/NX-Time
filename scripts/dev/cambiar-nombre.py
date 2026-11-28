"""Cambia el nombre del producto, su dominio y el paquete de la app, de una vez.

Uso:
    python scripts/dev/cambiar-nombre.py --nombre "Horas Claras" \\
        --dominio horasclaras.es --paquete es.horasclaras.app [--probar]

Opcionales: --correo-privacidad y --correo-seguridad (por defecto,
privacidad@ y seguridad@ del dominio nuevo) y --version-legal AAAA-MM-DD (por
defecto, hoy).

Cambia los sitios de la marca (ver scripts/dev/comprobar-marca.py):

- application.yml: `application.titular` (nombre, correos y web) y una
  versión nueva de las condiciones (`application.legal.version`), porque
  cambian el nombre y los correos de los textos legales: los ADMIN de cada
  empresa las aceptan otra vez al entrar.
- strings.xml: la entidad `&marca;` de la app.
- build.gradle.kts: `marcaPaquete` y `marcaHostWeb`.
- render.yaml: el dominio, en los valores y en los comentarios.
- assetlinks.json: el paquete que abre los enlaces de la web.
- SECURITY.md: el correo de seguridad y la web.
- docs/openapi.json: el título de la API (sale del nombre).
- comprobar-marca.py: el nombre y el dominio viejos pasan a RETIRADOS.

Y después regenera lo de la web (`npm run titular`). Es todo o nada: si algún
sitio no está como se espera, no escribe ningún fichero. Con --probar dice lo
que cambiaría, sin escribir.

Lo que NO hace, porque está fuera del código, lo lista al terminar y está en
docs/CAMBIO-DE-NOMBRE.md: el dominio en Cloudflare y Render, Brevo, Firebase
(con el paquete nuevo hace falta otro google-services.json), el SSO, el icono
(`ic_marca_frente.xml` y los SVG de la web) y las variables del panel de Render.
"""

import argparse
import datetime
import json
import pathlib
import re
import subprocess
import sys
from xml.sax.saxutils import escape

RAIZ = pathlib.Path(__file__).resolve().parents[2]

YML = "nx-time-backend/src/main/resources/application.yml"
STRINGS = "nx-time-frontend-android/src/main/res/values/strings.xml"
GRADLE = "nx-time-frontend-android/build.gradle.kts"
RENDER = "render.yaml"
ASSETLINKS = "nx-time-frontend-web/public/.well-known/assetlinks.json"
SECURITY = "SECURITY.md"
COMPROBAR = "scripts/dev/comprobar-marca.py"
OPENAPI = "docs/openapi.json"

PAQUETE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")
DOMINIO = re.compile(r"^(?=.{4,253}$)([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,}$")


class Fallo(Exception):
    pass


def una_vez(texto: str, patron: str, nuevo, fichero: str) -> str:
    """Sustituye `patron` (regex, multilínea) que tiene que estar exactamente una vez."""
    encontrados = re.findall(patron, texto, flags=re.M)
    if len(encontrados) != 1:
        raise Fallo(f"{fichero}: se esperaba una vez «{patron}» y sale {len(encontrados)}.")
    return re.sub(patron, nuevo, texto, count=1, flags=re.M)


def actual(texto: str, patron: str, fichero: str) -> str:
    m = re.search(patron, texto, flags=re.M)
    if not m:
        raise Fallo(f"{fichero}: no encuentro «{patron}».")
    return m.group(1)


def leer(relativa: str) -> str:
    return (RAIZ / relativa).read_text(encoding="utf-8")


def planear(args) -> tuple[dict[str, str], dict[str, str]]:
    """Los ficheros nuevos (ruta → contenido) y lo que había antes, sin escribir nada."""
    nuevos: dict[str, str] = {}

    # --- application.yml ---
    yml = leer(YML)
    antes = {
        "nombre": actual(yml, r'^    producto: "([^"]+)"', YML),
        "dominio": actual(yml, r'^    web: "https?://([^"/]+)/?"', YML),
        "privacidad": actual(yml, r'^    correo-privacidad: "([^"]+)"', YML),
        "seguridad": actual(yml, r'^    correo-seguridad: "([^"]+)"', YML),
        "version": actual(yml, r'^    version: "(\d{4}-\d{2}-\d{2})"', YML),
    }
    antes["paquete"] = actual(leer(GRADLE), r'^val marcaPaquete = "([^"]+)"', GRADLE)
    if args.version_legal <= antes["version"]:
        raise Fallo(f"La versión legal nueva ({args.version_legal}) tiene que ser posterior a {antes['version']}.")

    def comillas(valor: str) -> str:
        # titular.mjs solo admite "valor" sin comillas ni ${...} dentro.
        if '"' in valor or "$" in valor or "\\" in valor:
            raise Fallo(f"«{valor}»: sin comillas, $ ni barras invertidas.")
        return f'"{valor}"'

    yml = una_vez(yml, r'^    producto: "[^"]+"', f"    producto: {comillas(args.nombre)}", YML)
    yml = una_vez(yml, r'^    correo-privacidad: "[^"]+"', f"    correo-privacidad: {comillas(args.correo_privacidad)}", YML)
    yml = una_vez(yml, r'^    correo-seguridad: "[^"]+"', f"    correo-seguridad: {comillas(args.correo_seguridad)}", YML)
    yml = una_vez(yml, r'^    web: "https?://[^"]+"', f'    web: "https://{args.dominio}"', YML)
    yml = una_vez(yml, r'^    version: "\d{4}-\d{2}-\d{2}"', f'    version: "{args.version_legal}"', YML)
    nuevos[YML] = yml

    # --- La app ---
    nuevos[STRINGS] = una_vez(leer(STRINGS), r'^    <!ENTITY marca "[^"]*">',
                              f'    <!ENTITY marca "{escape(args.nombre, {chr(34): "&quot;"})}">', STRINGS)
    gradle = una_vez(leer(GRADLE), r'^val marcaPaquete = "[^"]+"', f'val marcaPaquete = "{args.paquete}"', GRADLE)
    nuevos[GRADLE] = una_vez(gradle, r'^val marcaHostWeb = "[^"]+"', f'val marcaHostWeb = "{args.dominio}"', GRADLE)

    # --- render.yaml: el dominio, en todas partes ---
    render = leer(RENDER)
    if antes["dominio"] not in render:
        raise Fallo(f"{RENDER}: no sale el dominio viejo ({antes['dominio']}).")
    nuevos[RENDER] = render.replace(antes["dominio"], args.dominio)

    # --- assetlinks.json ---
    enlaces = leer(ASSETLINKS)
    json.loads(enlaces)
    nuevos[ASSETLINKS] = una_vez(enlaces, r'"package_name": "[^"]+"', f'"package_name": "{args.paquete}"', ASSETLINKS)

    # --- SECURITY.md ---
    seguridad = leer(SECURITY)
    if antes["seguridad"] not in seguridad:
        raise Fallo(f"{SECURITY}: no sale el correo de seguridad viejo ({antes['seguridad']}).")
    nuevos[SECURITY] = seguridad.replace(antes["seguridad"], args.correo_seguridad).replace(
        f"https://{antes['dominio']}", f"https://{args.dominio}").replace(antes["nombre"], args.nombre)

    # --- docs/openapi.json: el título de la API (lo que daría actualizarOpenApi,
    # sin pasar la suite entera: es lo único del contrato que lleva el nombre) ---
    contrato = leer(OPENAPI)
    if contrato.count(antes["nombre"]) != 3:
        raise Fallo(f"{OPENAPI}: se esperaba el nombre 3 veces (título, descripción y contacto) "
                    f"y sale {contrato.count(antes['nombre'])}. Regenerarlo con actualizarOpenApi.")
    nuevos[OPENAPI] = contrato.replace(antes["nombre"], args.nombre)

    # --- comprobar-marca.py: los viejos, retirados ---
    comprobar = leer(COMPROBAR)
    retirados = actual(comprobar, r"^RETIRADOS: list\[str\] = (\[.*\])$", COMPROBAR)
    lista = json.loads(retirados)
    for viejo in (antes["nombre"], antes["dominio"]):
        if viejo not in lista and viejo not in (args.nombre, args.dominio):
            lista.append(viejo)
    nuevos[COMPROBAR] = una_vez(comprobar, r"^RETIRADOS: list\[str\] = \[.*\]$",
                                lambda _: f"RETIRADOS: list[str] = {json.dumps(lista, ensure_ascii=False)}", COMPROBAR)
    return nuevos, antes


def main() -> int:
    # En la consola de Windows, sin esto, una tilde o una flecha rompen el print.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    hoy = datetime.date.today().isoformat()
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--nombre", required=True, help="el nombre que se ve, p. ej. «Horas Claras»")
    parser.add_argument("--dominio", required=True, help="sin protocolo, p. ej. horasclaras.es")
    parser.add_argument("--paquete", required=True, help="applicationId de Android, p. ej. es.horasclaras.app")
    parser.add_argument("--correo-privacidad", help="por defecto, privacidad@<dominio>")
    parser.add_argument("--correo-seguridad", help="por defecto, seguridad@<dominio>")
    parser.add_argument("--version-legal", default=hoy, help=f"AAAA-MM-DD, por defecto hoy ({hoy})")
    parser.add_argument("--probar", action="store_true", help="decir qué cambiaría, sin escribir")
    args = parser.parse_args()

    args.nombre = args.nombre.strip()
    args.dominio = args.dominio.strip().lower()
    args.correo_privacidad = args.correo_privacidad or f"privacidad@{args.dominio}"
    args.correo_seguridad = args.correo_seguridad or f"seguridad@{args.dominio}"
    if not args.nombre or len(args.nombre) > 30:
        print("El nombre no puede ir vacío ni pasar de 30 caracteres (Play admite 30).")
        return 2
    if not DOMINIO.match(args.dominio):
        print(f"«{args.dominio}» no parece un dominio (sin https:// ni barras).")
        return 2
    if not PAQUETE.match(args.paquete):
        print(f"«{args.paquete}» no vale como applicationId (minúsculas y puntos, p. ej. es.horasclaras.app).")
        return 2
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", args.version_legal):
        print("--version-legal va como AAAA-MM-DD.")
        return 2

    try:
        nuevos, antes = planear(args)
    except Fallo as e:
        print(f"No se ha cambiado nada: {e}")
        return 1

    print(f"«{antes['nombre']}» → «{args.nombre}»")
    print(f"{antes['dominio']} → {args.dominio}")
    print(f"{antes['paquete']} → {args.paquete}")
    print(f"Condiciones {antes['version']} → {args.version_legal}")
    for ruta, contenido in nuevos.items():
        if contenido != leer(ruta):
            print(f"  {ruta}")
    if args.probar:
        print("\n(--probar: no se ha escrito nada.)")
        return 0

    for ruta, contenido in nuevos.items():
        (RAIZ / ruta).write_text(contenido, encoding="utf-8", newline="")
    web = RAIZ / "nx-time-frontend-web"
    subprocess.run("npm run titular --silent", cwd=web, shell=True, check=True)
    subprocess.run([sys.executable, str(RAIZ / COMPROBAR)], check=True)

    print(f"""
Hecho en el código. Lo que falta (docs/CAMBIO-DE-NOMBRE.md):
  1. Icono: redibujar ic_marca_frente.xml y los SVG de nx-time-frontend-web/public/iconos,
     y después `npm run iconos` (también saca el de Play y el gráfico de la ficha).
  2. Firebase: registrar la app {args.paquete} (y {args.paquete}.dev) con su SHA-1 y
     sustituir nx-time-frontend-android/google-services.json.
  3. Panel de Render: CORS_ALLOWED_ORIGINS y MAIL_FROM con el dominio nuevo.
  4. Cloudflare, Render (Custom Domains), Brevo y las URL de vuelta del SSO.
  5. Tests (backend check, web, Android) y un PR. Con la app nueva, retirar la vieja:
     APP_IDS_RETIRADOS={antes['paquete']} (ADR 064).""")
    return 0


if __name__ == "__main__":
    sys.exit(main())
