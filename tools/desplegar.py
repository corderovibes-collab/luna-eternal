#!/usr/bin/env python3
"""
Sube nuestros jars al servidor de desarrollo.

POR QUE EXISTE ESTO Y NO SE ARRASTRA EL JAR EN EL PANEL

Una subida se corrompio sin dar ningun error y el tamano coincidia. El servidor
arranco y murio con `Unexpected end of ZLIB input stream`, dentro de una traza
que no se parecia en nada a "el jar esta mal". Se perdio una tarde.

Este script hace las tres cosas que evitan repetirlo:

  1. BORRA el jar viejo antes de subir. Sobrescribir es lo que fallo.
  2. Sube por el endpoint de binarios (URL firmada + multipart), no por el de
     edicion de texto.
  3. Compara el tamano que reporta el panel con el del fichero local, y falla
     si no cuadra.

Uso:
    python tools/desplegar.py neon           # sube neon/build/libs/*.jar
    python tools/desplegar.py mod            # sube mod/build/libs/*.jar
    python tools/desplegar.py neon mod       # los dos
    python tools/desplegar.py neon --reiniciar
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ptero  # noqa: E402

RAIZ = Path(__file__).resolve().parent.parent

# proyecto -> (carpeta, prefijo del jar). El prefijo se usa para encontrar y
# borrar la version anterior aunque haya cambiado el numero.
PROYECTOS = {
    "mod":  ("mod",  "lunaeternal"),
    "neon": ("neon", "lunaneon"),
}


def jar_local(carpeta: str, prefijo: str) -> Path:
    libs = RAIZ / carpeta / "build" / "libs"
    # `-sources.jar` y `-dev.jar` los produce Loom y no son el mod: el que vale
    # es el remapeado a mapeos oficiales, que es el de nombre corto.
    candidatos = [j for j in libs.glob(f"{prefijo}-*.jar")
                  if not j.stem.endswith(("-sources", "-dev"))]
    if not candidatos:
        raise SystemExit(f"No hay jar en {libs}. Compila primero:\n"
                         f"    cd {carpeta} && bash build.sh")
    return max(candidatos, key=lambda j: j.stat().st_mtime)


def desplegar(nombre: str) -> None:
    carpeta, prefijo = PROYECTOS[nombre]
    jar = jar_local(carpeta, prefijo)
    datos = jar.read_bytes()
    print(f"\n{nombre.upper()}  ·  {jar.name}  ({len(datos) / 1024:.0f} KB)")

    viejos = [n for n, _, es_fichero in ptero.listar("/mods")
              if es_fichero and n.startswith(prefijo) and n.endswith(".jar")]
    if viejos:
        ptero.borrar("/mods", viejos)
        print(f"  borrado  {', '.join(viejos)}")

    tam = ptero.subir("/mods", jar.name, datos)
    if tam != len(datos):
        raise SystemExit(f"  CORRUPTO: el panel dice {tam} B y el fichero tiene "
                         f"{len(datos)} B. NO reinicies el servidor.")
    print(f"  subido   {jar.name}  ({tam} B, tamano verificado)")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("proyectos", nargs="+", choices=sorted(PROYECTOS),
                    help="que jars subir")
    ap.add_argument("--reiniciar", action="store_true",
                    help="reinicia el servidor al terminar (los mods solo se "
                         "cargan al arrancar)")
    args = ap.parse_args()

    for nombre in args.proyectos:
        desplegar(nombre)

    if args.reiniciar:
        # Se avisa por el chat antes: quien este construyendo con Axiom pierde
        # la seleccion, no el trabajo, pero agradece el aviso.
        ptero.comando("say Reiniciando para cargar los mods. Vuelvo en 30 s.")
        ptero.potencia("restart")
        print("\n  Reiniciando. Un mod nuevo NO se carga en caliente.")
    else:
        print("\n  Los mods se cargan AL ARRANCAR. Para que surtan efecto:")
        print("      python tools/desplegar.py " + " ".join(args.proyectos)
              + " --reiniciar")


if __name__ == "__main__":
    main()
