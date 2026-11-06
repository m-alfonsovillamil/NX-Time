"""Aplica varias sustituciones de texto exactas, en varios ficheros a la vez.

Uso:
    python scripts/dev/sustituir.py cambios.py [--raiz RUTA] [--probar]

`cambios.py` (por ejemplo, en el scratchpad) define una lista:

    CAMBIOS = [
        ("ruta/relativa/Fichero.java", r'''texto viejo''', r'''texto nuevo'''),
        ...
    ]

Cada texto viejo tiene que aparecer EXACTAMENTE una vez en su fichero. Si
alguno falla, no se escribe nada. Sirve para cambios que con un heredoc de
bash se rompen (barras invertidas, comillas, varias líneas).

Los saltos de línea se conservan tal como están en el fichero (LF o CRLF).
Con --probar solo comprueba, sin escribir.
"""

import argparse
import pathlib
import runpy
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("cambios", help="fichero .py que define CAMBIOS")
    parser.add_argument("--raiz", default=".", help="carpeta de la que cuelgan las rutas")
    parser.add_argument("--probar", action="store_true", help="comprobar sin escribir")
    args = parser.parse_args()

    cambios = runpy.run_path(args.cambios).get("CAMBIOS")
    if not cambios:
        print(f"{args.cambios} no define CAMBIOS")
        return 2

    raiz = pathlib.Path(args.raiz)
    contenidos: dict[pathlib.Path, str] = {}
    saltos: dict[pathlib.Path, str] = {}
    errores = []

    for i, (ruta, viejo, nuevo) in enumerate(cambios, 1):
        fichero = raiz / ruta
        if fichero not in contenidos:
            if not fichero.exists():
                errores.append(f"#{i} {ruta}: no existe")
                continue
            crudo = fichero.read_bytes().decode("utf-8")
            saltos[fichero] = "\r\n" if "\r\n" in crudo else "\n"
            contenidos[fichero] = crudo.replace("\r\n", "\n")
        texto = contenidos[fichero]
        viejo_n = viejo.replace("\r\n", "\n")
        veces = texto.count(viejo_n)
        if veces != 1:
            errores.append(f"#{i} {ruta}: el texto viejo aparece {veces} veces")
            continue
        contenidos[fichero] = texto.replace(viejo_n, nuevo.replace("\r\n", "\n"))

    if errores:
        print("No se ha escrito nada:")
        print("\n".join("  " + e for e in errores))
        return 1

    if not args.probar:
        for fichero, texto in contenidos.items():
            fichero.write_bytes(texto.replace("\n", saltos[fichero]).encode("utf-8"))
    accion = "Comprobados" if args.probar else "Aplicados"
    print(f"{accion} {len(cambios)} cambios en {len(contenidos)} ficheros.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
