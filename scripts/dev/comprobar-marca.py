"""Comprueba que el nombre del producto y el dominio solo están en su sitio.

Uso:
    python scripts/dev/comprobar-marca.py

El nombre («NX Time») está cogido y va a cambiar (docs/CAMBIO-DE-NOMBRE.md).
Para que el cambio sea un comando (scripts/dev/cambiar-nombre.py), cada módulo
lo lee de UN sitio:

- backend: `application.titular` de application.yml (CondicionesDelServicio);
  los asuntos de correo llevan `{producto}`;
- web: `src/legal/titular.ts`, generado por `npm run titular`, que pone además
  el nombre en index.html, el manifest y sw.js;
- app: la entidad `&marca;` de `res/values/strings.xml`, y el paquete y el
  dominio en `build.gradle.kts` (`marcaPaquete`, `marcaHostWeb`);
- la configuración de producción, en render.yaml.

Esto falla (con el fichero y la línea) si el nombre o el dominio aparecen
escritos en un texto que se ve —código, plantillas, recursos, ficheros
públicos— fuera de esos sitios. Los comentarios, los tests, las migraciones y
la documentación pueden nombrarlo: no los ve nadie que use la app.

Se buscan el nombre y el dominio de application.yml y los de RETIRADOS: los
nombres viejos, que cambiar-nombre.py apunta aquí para que no quede ninguno.

Las líneas de comentario se reconocen por cómo empiezan (`*`, `//`, `/*`, `#`)
y en XML/HTML se quitan los `<!-- -->`. Un comentario al final de una línea de
código cuenta como código: si nombra el producto, se pasa a su propia línea.
"""

import pathlib
import re
import sys

RAIZ = pathlib.Path(__file__).resolve().parents[2]

# Los nombres y dominios de antes de un cambio de nombre (los añade
# cambiar-nombre.py). Ninguno puede quedar en un texto visible.
RETIRADOS: list[str] = []

CONFIGURACION = RAIZ / "nx-time-backend" / "src" / "main" / "resources" / "application.yml"

# Qué se mira: (carpeta, patrones de fichero).
CODIGO = [
    ("nx-time-backend/src/main/java", ["*.java"]),
    ("nx-time-frontend-android/src/main/java", ["*.kt"]),
    ("nx-time-frontend-web/src", ["*.ts", "*.tsx", "*.js", "*.mjs"]),
    ("nx-time-frontend-web/public", ["*.js"]),
]
MARCADO = [
    ("nx-time-backend/src/main/resources/templates", ["*.html"]),
    ("nx-time-frontend-android/src/main/res", ["*.xml"]),
    ("nx-time-frontend-android/src/main", ["AndroidManifest.xml"]),
    ("nx-time-frontend-web", ["index.html"]),
    ("nx-time-frontend-web/public", ["*.webmanifest", "*.html"]),
]

# Ficheros que son el sitio del nombre, o que se generan desde él.
GENERADOS = {
    "nx-time-frontend-web/src/legal/titular.ts",
    "nx-time-frontend-web/src/api/schema.d.ts",
}

# Líneas permitidas, por fichero: el sitio del nombre dentro de ese fichero.
PERMITIDAS = {
    "nx-time-frontend-android/src/main/res/values/strings.xml": re.compile(r"^\s*<!ENTITY marca "),
    "nx-time-frontend-web/index.html": re.compile(r"^\s*<title>[^<]*</title>\s*$"),
    "nx-time-frontend-web/public/manifest.webmanifest": re.compile(r'^\s*"(short_)?name": '),
    "nx-time-frontend-web/public/sw.js": re.compile(r"datos\.titulo \|\| "),
}

# En las plantillas de Thymeleaf, lo que hay dentro de un elemento con
# th:text se sustituye al pintar: es solo para ver la plantilla en un navegador.
RELLENO_THYMELEAF = re.compile(r'(th:text="\$\{producto\}"[^>]*>)[^<]*')

COMENTARIO_XML = re.compile(r"<!--.*?-->", re.S)
INICIO_COMENTARIO = ("*", "//", "/*", "{/*", "#")


def leer_titular() -> tuple[str, str]:
    """El nombre y el dominio de application.titular (sin protocolo)."""
    texto = CONFIGURACION.read_text(encoding="utf-8")
    producto = re.search(r'^\s{4}producto: "([^"]+)"', texto, re.M)
    web = re.search(r'^\s{4}web: "https?://([^"/]+)/?"', texto, re.M)
    if not producto or not web:
        raise SystemExit("No encuentro application.titular.producto y .web en application.yml")
    return producto.group(1), web.group(1)


def ficheros(grupos):
    for carpeta, patrones in grupos:
        base = RAIZ / carpeta
        for patron in patrones:
            # index.html y AndroidManifest.xml van sueltos: sin bajar a subcarpetas.
            encontrados = base.glob(patron) if "." in patron and "*" not in patron else base.rglob(patron)
            for ruta in encontrados:
                relativa = ruta.relative_to(RAIZ).as_posix()
                if "/node_modules/" in relativa or "/build/" in relativa or "/dist/" in relativa:
                    continue
                if relativa in GENERADOS or ".test." in ruta.name or "/pruebas/" in relativa:
                    continue
                yield ruta, relativa


def sin_comentarios_xml(texto: str) -> str:
    # Cada comentario se cambia por tantos saltos de línea como tenía, para
    # que los números de línea sigan valiendo.
    return COMENTARIO_XML.sub(lambda m: "\n" * m.group(0).count("\n"), texto)


def buscar(nombres: list[str]) -> list[str]:
    hallazgos = []
    patron = re.compile("|".join(re.escape(n) for n in nombres), re.I)

    def revisar(relativa, lineas, es_codigo):
        permitida = PERMITIDAS.get(relativa)
        for numero, linea in enumerate(lineas, start=1):
            if not patron.search(linea):
                continue
            if es_codigo and linea.lstrip().startswith(INICIO_COMENTARIO):
                continue
            if permitida and permitida.search(linea):
                continue
            hallazgos.append(f"{relativa}:{numero}: {linea.strip()[:160]}")

    for ruta, relativa in ficheros(CODIGO):
        revisar(relativa, ruta.read_text(encoding="utf-8").split("\n"), es_codigo=True)
    for ruta, relativa in ficheros(MARCADO):
        texto = sin_comentarios_xml(ruta.read_text(encoding="utf-8"))
        texto = RELLENO_THYMELEAF.sub(r"\1", texto)
        revisar(relativa, texto.split("\n"), es_codigo=False)
    return hallazgos


def main() -> int:
    # En la consola de Windows, sin esto, una tilde o una flecha rompen el print.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    producto, dominio = leer_titular()
    nombres = [producto, dominio, *RETIRADOS]
    hallazgos = buscar(nombres)
    if hallazgos:
        print(f"El nombre o el dominio ({', '.join(nombres)}) están escritos fuera de su sitio:\n")
        print("\n".join(hallazgos))
        print("\nTómalos de la configuración (ver el comentario de scripts/dev/comprobar-marca.py).")
        return 1
    print(f"Marca en su sitio: {', '.join(nombres)} solo en la configuración.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
