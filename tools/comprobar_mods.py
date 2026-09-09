#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QUE MODS DEL PACK SE HAN QUEDADO ATRAS.

    python tools/comprobar_mods.py
    python tools/comprobar_mods.py --solo-cobblemon

⚠⚠⚠ ESTO EXISTE PORQUE UN MOD VIEJO TIRO EL SERVIDOR TRES VECES (2026-09-09).

`cobblenav` se quedo en la version de ABRIL cuando se subio a Cobblemon 1.8.0:
su mixin llamaba a `SpawnDetail.getBucket()`, que 1.8.0 ya no tiene, y el
servidor moria con `NoSuchMethodError` **cada vez que un Pokemon salvaje
intentaba aparecer cerca de alguien**.

⚠⚠ Y ESTUVO UN DIA ENTERO SIN VERSE. No fallaba al arrancar --el pack cargaba
   perfectamente-- ni al conectarse: solo cuando habia alguien EN UN MUNDO CON
   SPAWNS. Quien probaba estaba en la ciudadela y en el lobby, que son
   dimensiones de vacio. Lo destapo la primera cuenta nueva que piso el Hogar.

   Por eso una lista de «mods actualizados» escrita a mano no basta: la ronda de
   Cobblemon 1.8.0 actualizo DOCE addons y se dejo este, y nada lo dijo. Esto lo
   pregunta, que es distinto de recordarlo.

COMO SABE QUE PROYECTO ES CADA JAR

Del propio manifiesto: las URL del CDN de Modrinth llevan el id dentro
(`/data/<proyecto>/versions/<version>/<fichero>.jar`). No hace falta adivinar
por el nombre del fichero -- que es justo lo que ya mordio dos veces con los
slugs (`repurposed-structures-fabric`, `cobblemon-pokenav`).
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
MANIFIESTO = RAIZ / "build" / "pack" / "manifest.json"
UA = {"User-Agent": "PokeReport-LunaEternal/0.1 (comprobar_mods)"}
MC = "1.21.1"

# ⚠ El CDN mete el id del proyecto y el de la version en la ruta.
CDN = re.compile(r"cdn\.modrinth\.com/data/([A-Za-z0-9]+)/versions/([A-Za-z0-9]+)/")


def pedir(url):
    r = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(r, timeout=30) as f:
        return json.load(f)


def del_manifiesto():
    """Los mods del manifiesto publicado, con su proyecto y version."""
    if not MANIFIESTO.exists():
        sys.exit("No hay build/pack/manifest.json. Ejecuta antes gen_manifest.py")
    m = json.loads(MANIFIESTO.read_text(encoding="utf-8"))
    salida = []
    for f in m["files"]:
        ruta = f.get("path", "")
        if not ruta.startswith("mods/"):
            continue
        for u in f.get("urls", []) or [f.get("url", "")]:
            g = CDN.search(u or "")
            if g:
                salida.append((ruta.split("/")[-1], g.group(1), g.group(2)))
                break
    return salida


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--solo-cobblemon", action="store_true",
                    help="solo los que dependen de Cobblemon, que son los que "
                         "revientan al subir su version")
    args = ap.parse_args()

    mods = del_manifiesto()
    print(f"  {len(mods)} mods del manifiesto vienen de Modrinth\n")

    atrasados, fallos, aldia = [], [], 0
    for i, (jar, proyecto, version_actual) in enumerate(mods, 1):
        try:
            vs = pedir(f"https://api.modrinth.com/v2/project/{proyecto}/version")
        except urllib.error.HTTPError as e:
            fallos.append((jar, f"HTTP {e.code}"))
            continue
        except Exception as e:                                   # noqa: BLE001
            fallos.append((jar, str(e)[:40]))
            continue

        # ⚠ Solo versiones de NUESTRO Minecraft y NUESTRO cargador. Sin este
        #   filtro, la "ultima" seria la de otra version del juego y este
        #   informe mandaria a actualizar a algo que no arranca.
        buenas = [v for v in vs
                  if MC in v["game_versions"] and "fabric" in v["loaders"]]
        if not buenas:
            fallos.append((jar, "sin versiones para " + MC))
            continue

        ultima = buenas[0]          # Modrinth las devuelve de nueva a vieja
        depende_cobblemon = any(
            d.get("project_id") == "MdwFAVRL" for d in ultima.get("dependencies", []))
        if args.solo_cobblemon and not depende_cobblemon:
            continue

        if ultima["id"] == version_actual:
            aldia += 1
        else:
            actual = next((v for v in vs if v["id"] == version_actual), None)
            atrasados.append({
                "jar": jar,
                # El SLUG, que es lo que hay que escribir en SUBIR -- y casi
                # nunca es el nombre del jar. Sin el, quien lea este informe
                # tiene que ir a buscarlo a mano, y ahi es donde se equivoca:
                # una clave mal escrita no hace nada y no se queja.
                "slug": pedir(f"https://api.modrinth.com/v2/project/{proyecto}")["slug"],
                "tengo": actual["version_number"] if actual else "?",
                "fecha_tengo": actual["date_published"][:10] if actual else "?",
                "hay": ultima["version_number"],
                "fecha_hay": ultima["date_published"][:10],
                "cobblemon": depende_cobblemon,
            })
        if i % 25 == 0:
            time.sleep(1)           # cortesia con la API

    # ⚠⚠ LOS QUE DEPENDEN DE COBBLEMON VAN PRIMERO Y APARTE. Un mod de interfaz
    #    atrasado es una funcion vieja; un ADDON DE COBBLEMON atrasado es un
    #    `NoSuchMethodError` esperando a que alguien juegue.
    conc = [a for a in atrasados if a["cobblemon"]]
    resto = [a for a in atrasados if not a["cobblemon"]]

    if conc:
        print("  ⚠⚠⚠ ADDONS DE COBBLEMON ATRASADOS — son los que CRASHEAN")
        for a in conc:
            print(f"    {a['slug']:<34} {a['tengo']} -> {a['hay']}"
                  f"   ({a['fecha_hay']})")
        print()
    if resto:
        print("  Atrasados (no dependen de Cobblemon)")
        for a in resto:
            print(f"    {a['slug']:<34} {a['tengo']} -> {a['hay']}"
                  f"   ({a['fecha_hay']})")
        print()
    if fallos:
        print("  No se pudo comprobar")
        for j, m in fallos:
            print(f"    {j:<52} {m}")
        print()

    print(f"  al dia {aldia} · atrasados {len(atrasados)}"
          f" (de Cobblemon: {len(conc)}) · sin comprobar {len(fallos)}")
    # ⚠ Sale distinto de cero si hay un addon de Cobblemon atrasado: eso no es
    #   una sugerencia, es la clase de cosa que tira el servidor.
    return 1 if conc else 0


if __name__ == "__main__":
    raise SystemExit(main())
