"""Comprueba que las librerías nativas de un APK o AAB valen para páginas de 16 KB.

Uso:
    python scripts/dev/comprobar-16kb.py app.apk [app.aab ...]

Google Play exige desde noviembre de 2025 que las apps con código nativo
funcionen en móviles con páginas de memoria de 16 KB. La app no tiene código
nativo propio, pero sus dependencias de androidx traen dos `.so`
(`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`). Para que
valgan:

- cada segmento PT_LOAD del ELF tiene que estar alineado a 16 KB o más
  (`p_align >= 16384`): eso lo decide quien compila la librería;
- y en el APK, una `.so` sin comprimir tiene que empezar en un desplazamiento
  múltiplo de 16 KB dentro del zip: eso lo hace el empaquetado de AGP.

Sale con 1 y dice cuál falla si alguna no cumple. Sin dependencias: lee el zip
y las cabeceras ELF a mano.
"""

import struct
import sys
import zipfile

PAGINA = 16 * 1024
PT_LOAD = 1


def alineamientos_load(elf: bytes) -> list[int]:
    """Los p_align de los segmentos PT_LOAD de un ELF de 32 o 64 bits."""
    if elf[:4] != b"\x7fELF":
        raise ValueError("no es un ELF")
    clase, datos = elf[4], elf[5]
    orden = "<" if datos == 1 else ">"
    if clase == 2:  # 64 bits
        e_phoff, = struct.unpack_from(orden + "Q", elf, 0x20)
        e_phentsize, e_phnum = struct.unpack_from(orden + "HH", elf, 0x36)
        formato, desp_align = orden + "I", 0x30
    else:  # 32 bits
        e_phoff, = struct.unpack_from(orden + "I", elf, 0x1C)
        e_phentsize, e_phnum = struct.unpack_from(orden + "HH", elf, 0x2A)
        formato, desp_align = orden + "I", 0x1C
    resultado = []
    for i in range(e_phnum):
        base = e_phoff + i * e_phentsize
        p_type, = struct.unpack_from(orden + "I", elf, base)
        if p_type != PT_LOAD:
            continue
        if clase == 2:
            p_align, = struct.unpack_from(orden + "Q", elf, base + desp_align)
        else:
            p_align, = struct.unpack_from(formato, elf, base + desp_align)
        resultado.append(p_align)
    return resultado


def desplazamiento_de_datos(zip_abierto: zipfile.ZipFile, info: zipfile.ZipInfo) -> int:
    """Dónde empiezan los datos de una entrada (tras su cabecera local)."""
    zip_abierto.fp.seek(info.header_offset)
    cabecera = zip_abierto.fp.read(30)
    largo_nombre, largo_extra = struct.unpack_from("<HH", cabecera, 26)
    return info.header_offset + 30 + largo_nombre + largo_extra


def revisar(ruta: str) -> list[str]:
    fallos = []
    es_apk = ruta.lower().endswith(".apk")
    with zipfile.ZipFile(ruta) as paquete:
        librerias = [i for i in paquete.infolist() if i.filename.endswith(".so")]
        for info in librerias:
            # Las de x86 (emulador) también cuentan: Play las revisa todas.
            for p_align in alineamientos_load(paquete.read(info)):
                if p_align < PAGINA:
                    fallos.append(f"{ruta}: {info.filename}: segmento LOAD alineado a {p_align} (< 16 KB)")
                    break
            if es_apk and info.compress_type == zipfile.ZIP_STORED:
                inicio = desplazamiento_de_datos(paquete, info)
                if inicio % PAGINA:
                    fallos.append(f"{ruta}: {info.filename}: sin comprimir y en el byte {inicio}, no múltiplo de 16 KB")
        print(f"{ruta}: {len(librerias)} librerías nativas revisadas")
    return fallos


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    fallos = [f for ruta in sys.argv[1:] for f in revisar(ruta)]
    if fallos:
        print("\n".join(fallos))
        return 1
    print("Todo alineado a 16 KB.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
