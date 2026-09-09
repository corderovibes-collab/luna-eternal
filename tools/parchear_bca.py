#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
COBBLEMON ADDITIONS, SIN GENERAR CONSTRUCCIONES Y SIN COBBLEDOLLARS.

    python tools/parchear_bca.py

Baja el jar oficial, le quita tres cosas y deja el resto intacto:

    1. La dependencia de `cobbledollars` en fabric.mod.json
    2. Toda la generacion de estructuras (y las tablas de botin que las llenan)
    3. Los spawn pools, que referencian esas estructuras por id

Lo que SE QUEDA: los 59 cuadros, el libro de la Pokedex, los tres spawners, los
NPCs con dialogo, y todo el codigo del mod.

⚠⚠⚠ POR QUE HAY QUE PARCHEARLO EN VEZ DE USARLO O QUITARLO

El pack lo traia en la 4.1.6 (diciembre de 2025), anterior a Cobblemon 1.8.0.
Actualizar a la 4.3.0 es lo correcto -- pero su 4.3.0 **exige `cobbledollars`**,
que el usuario mando quitar el 2026-09-04: «ganas dinero y eso con el sistema de
economia que tenemos no debe de estar». Aceptarlo seria una SEGUNDA economia,
que es justo lo que D-040 y esa exclusion evitan.

Y ademas el mod **genera construcciones por el mundo**, que el usuario ya
rechazo cuatro veces (legendary-monuments, repurposed-structures-fabric,
biome-replacer, huge-structure-blocks). Se colo porque su nombre no dice
«structures»: son 298 piezas .nbt, 11 estructuras, 41 plantillas, y **pisa los
`structure_set` de VAINILLA** (`minecraft:villages` y `minecraft:swamp_huts`).

⚠⚠ QUITAR EL `structure_set` DEVUELVE LAS ALDEAS DE VAINILLA. El mod no las
   añade: las **sobrescribe**. Al borrar su fichero vuelve a mandar el de
   Minecraft, que es lo que se quiere.

⚠⚠⚠ Y LOS SPAWN POOLS TIENEN QUE IRSE CON LAS ESTRUCTURAS, aunque nadie los
   haya pedido quitar. Dicen literalmente:

       "structures": ["bca:village/witch_hut"]

   o sea que nombran una estructura que ya no existiria. Cobblemon no resuelve
   ese id y **el pool no parsea**: es exactamente el fallo de las «55 tablas de
   botin de los entrenadores» que ya mordio aqui. Ademas eran spawns de Gen 5
   --Purrloin, Liepard, Hatenna...-- y un Meowth de Galar, que el datapack de
   generaciones tiene que apagar uno a uno (D-017). Se van por necesidad y de
   paso por diseño.

⚠ SOLO UNA CLASE NOMBRA `cobbledollars`, y es una CADENA en una lista de
  configuracion (`ConfigData`), no una llamada. Comprobado con `javap` antes de
  tocar la dependencia: si el codigo lo usara de verdad, quitar la linea del
  fabric.mod.json solo cambiaria un fallo de arranque por uno de ejecucion.

⚠⚠ LA VERSION VA FIJADA POR ID, NO POR «LA ULTIMA». Es la leccion de
   cards/parchear.py: con «la ultima», dos ejecuciones de dias distintos dan
   jars distintos y nadie se entera hasta que algo falla.
"""
from __future__ import annotations

import json
import hashlib
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SALIDA = RAIZ / "bca" / "build" / "libs"
UA = {"User-Agent": "PokeReport-LunaEternal/0.1 (parchear_bca)"}

# Fijado a dedo. Ver la cabecera.
VERSION = "4.3.0"
URL = ("https://cdn.modrinth.com/data/W2pr9jyL/versions/NVitD9gY/"
       "cobblemon-additions-4.3.0.jar")
SHA1 = "bede157e2378d44434d39514fbcbabfcba72a0f7"

# El sufijo dice que NO es el jar de nadie mas. Igual que cobblemon-cards-luna1.
NOMBRE = f"cobblemon-additions-{VERSION}-luna1.jar"

# Lo que se quita. Cada prefijo TIENE que casar con algo: si no, se aborta.
FUERA = {
    "data/minecraft/worldgen/":
        "pisa los structure_set de VAINILLA (villages y swamp_huts). Al "
        "quitarlo vuelven a mandar los de Minecraft",
    "data/bca/worldgen/":
        "sus 11 estructuras y 41 plantillas de generacion",
    "data/bca/structure/":
        "las 298 piezas .nbt de las construcciones",
    "data/bca/loot_table/":
        "el botin de los cofres de esas construcciones. Sin construcciones no "
        "lo abre nadie",
    "data/bca/tags/worldgen/":
        "las etiquetas de bioma que decidian DONDE generarlas",
    "data/cobblemon/spawn_pool_world/":
        "nombran 'bca:village/...' por id: sin las estructuras NO PARSEAN. Y "
        "eran spawns de Gen 5 que el datapack tiene que apagar (D-017)",
}


def bajar() -> bytes:
    print(f"  bajando {URL.split('/')[-1]}")
    with urllib.request.urlopen(urllib.request.Request(URL, headers=UA),
                                timeout=120) as f:
        d = f.read()
    h = hashlib.sha1(d).hexdigest()
    # ⚠ Se comprueba la huella: este jar se ejecuta en la maquina de cada
    #   jugador, y bajarlo sin mirar que es lo que creemos es lo mismo que
    #   confiar en la red.
    if h != SHA1:
        sys.exit(f"  El sha1 NO cuadra.\n    esperaba {SHA1}\n    llego    {h}")
    print(f"  sha1 verificado ({len(d)} B)")
    return d


def main() -> int:
    d = bajar()
    origen = zipfile.ZipFile(__import__("io").BytesIO(d))
    nombres = origen.namelist()

    # ⚠⚠⚠ UN PREFIJO QUE NO CASA CON NADA ES UN RECORTE QUE NO OCURRE, y hoy
    #    mismo esa forma de fallo costo un servidor caido: una clave de SUBIR
    #    mal escrita se quedo sin hacer nada y sin quejarse. Aqui se aborta.
    huerfanos = [p for p in FUERA if not any(n.startswith(p) for n in nombres)]
    if huerfanos:
        sys.exit("  Estos prefijos no casan con nada del jar (¿cambio de "
                 "version?):\n    " + "\n    ".join(huerfanos))

    SALIDA.mkdir(parents=True, exist_ok=True)
    # ⚠ Se limpian los jars viejos: `jar_propio` coge el MAS RECIENTE de la
    #   carpeta, asi que dejar uno anterior es una ruleta.
    for viejo in SALIDA.glob("cobblemon-additions-*.jar"):
        viejo.unlink()

    destino = SALIDA / NOMBRE
    quitados = {p: 0 for p in FUERA}
    copiados = 0

    with zipfile.ZipFile(destino, "w", zipfile.ZIP_DEFLATED) as z:
        for n in nombres:
            fuera = next((p for p in FUERA if n.startswith(p)), None)
            if fuera:
                quitados[fuera] += 1
                continue
            datos = origen.read(n)
            if n == "fabric.mod.json":
                fm = json.loads(datos)
                if "cobbledollars" not in fm.get("depends", {}):
                    sys.exit("  fabric.mod.json ya NO depende de cobbledollars: "
                             "revisa si este parche sigue haciendo falta.")
                del fm["depends"]["cobbledollars"]
                # ⚠ Y de `suggests` tambien, para que ningun launcher lo
                #   proponga: sugerido o exigido, lo que no se quiere es que
                #   entre.
                fm.get("suggests", {}).pop("cobbledollars", None)
                datos = json.dumps(fm, indent=2, ensure_ascii=False).encode()
                print("  fabric.mod.json: fuera la dependencia de cobbledollars")
            z.writestr(n, datos)
            copiados += 1

    print()
    for p, motivo in FUERA.items():
        print(f"  -{quitados[p]:4d}  {p}")
        print(f"        {motivo}")
    print()
    print(f"  quedan {copiados} ficheros  ->  {destino.relative_to(RAIZ)}")
    print("  (cuadros, libro de la Pokedex, spawners, NPCs y todo el codigo)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
