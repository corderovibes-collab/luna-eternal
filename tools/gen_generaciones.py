#!/usr/bin/env python3
"""
Genera el datapack que limita el servidor a Kanto y Johto (D-017).

Lee el codigo fuente de Cobblemon (vendor/cobblemon) y escribe un datapack que
apaga los spawns de todo lo que no sea generacion 1 o 2, poniendo
"enabled": false en cada fichero de spawn_pool_world.

POR QUE ASI Y NO BORRANDO NADA
  - Es reversible: activar una generacion es regenerar con otro parametro.
  - No toca el jar de Cobblemon, que puede actualizarse por debajo.
  - Los datos siguen existiendo: modelos, movimientos y evoluciones estan ahi
    desde el primer dia, solo que esas especies no aparecen solas.

LO QUE **NO** SE APAGA: LAS EVOLUCIONES
  23 especies de Kanto/Johto evolucionan a generaciones posteriores. Apagar el
  spawn NO impide evolucionar, y eso es DELIBERADO: esas 23 pasan a existir
  unicamente por evolucion, nunca por aparicion natural. Es decir, se
  convierten en las especies mas dificiles del servidor, y solo las consigue
  quien sabe como.

  El caso que mejor lo resume: ursaring -> ursaluna exige LUNA LLENA de noche.
  Es exactamente el pilar del juego, y bloquearlo seria tirar identidad.

Uso:
    python tools/gen_generaciones.py                 # Kanto + Johto
    python tools/gen_generaciones.py 1 2 3           # anadir Hoenn
"""
import json
import os
import sys
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
FUENTE = RAIZ / "vendor" / "cobblemon" / "common" / "src" / "main" / "resources" / "data" / "cobblemon"
SALIDA = RAIZ / "build" / "datapacks"

DESCRIPCION = (
    "Luna Eternal: limita las apariciones naturales a las generaciones activas. "
    "Las demas especies siguen existiendo y son alcanzables por evolucion."
)


def generaciones_activas(argv):
    nums = [a for a in argv[1:] if a.isdigit()]
    if not nums:
        nums = ["1", "2"]
    return {f"generation{n}" for n in nums}


def leer_especies():
    """name -> (generacion, datos)."""
    especies = {}
    carpeta = FUENTE / "species"
    if not carpeta.exists():
        sys.exit(f"No encuentro el fuente de Cobblemon en {FUENTE}\n"
                 f"Clonalo con: git clone --depth 1 "
                 f"https://gitlab.com/cable-mc/cobblemon.git vendor/cobblemon")
    for gen in sorted(os.listdir(carpeta)):
        for fichero in (carpeta / gen).glob("*.json"):
            try:
                datos = json.loads(fichero.read_text(encoding="utf-8"))
            except Exception:
                continue
            nombre = (datos.get("name") or "").lower()
            if nombre:
                especies[nombre] = (gen, datos)
    return especies


def normalizar(nombre):
    """
    Quita todo lo que no sea letra o numero.

    Hace falta porque los nombres de fichero y los del JSON no coinciden en
    los Pokemon con caracteres raros: farfetch'd/farfetchd, mr. mime/mrmime,
    porygon-z/porygonz, jangmo-o/jangmoo, nidoran-f/nidoranf.

    Sin esto, 11 ficheros quedaban sin resolver — y cuatro de ellos
    (porygonz, mimejr, jangmoo, hakamoo) son de generaciones posteriores que
    se habrian quedado ACTIVOS por error.
    """
    return "".join(ch for ch in nombre.lower() if ch.isalnum())


def especie_del_spawn(fichero, especies, indice_normalizado):
    """
    Deduce la especie de un fichero de spawn. Se mira primero el contenido
    (fiable) y despues el nombre del fichero, ambos normalizados.
    """
    try:
        datos = json.loads(fichero.read_text(encoding="utf-8"))
    except Exception:
        return None

    for spawn in datos.get("spawns", []):
        pokemon = (spawn.get("pokemon") or "").split()[0]
        clave = indice_normalizado.get(normalizar(pokemon))
        if clave:
            return clave

    partes = fichero.stem.split("_", 1)
    if len(partes) == 2:
        clave = indice_normalizado.get(normalizar(partes[1]))
        if clave:
            return clave
    return None


def evoluciones_que_cruzan(especies, activas):
    """Especies activas que evolucionan fuera del rango activo."""
    nombres_activos = {n for n, (g, _) in especies.items() if g in activas}
    fugas = []
    for nombre in sorted(nombres_activos):
        _, datos = especies[nombre]
        for ev in (datos.get("evolutions") or []):
            destino = (ev.get("result") or "").split()[0].lower()
            if destino and destino in especies and destino not in nombres_activos:
                fugas.append((nombre, destino, especies[destino][0]))
    return fugas


def main():
    activas = generaciones_activas(sys.argv)
    especies = leer_especies()
    nombres_activos = {n for n, (g, _) in especies.items() if g in activas}
    indice = {normalizar(n): n for n in especies}

    print(f"Generaciones activas: {', '.join(sorted(activas))}")
    print(f"Especies activas:     {len(nombres_activos)} de {len(especies)}")

    SALIDA.mkdir(parents=True, exist_ok=True)
    zip_salida = SALIDA / "luna-generaciones.zip"

    apagadas = 0
    conservadas = 0
    sin_resolver = []

    with zipfile.ZipFile(zip_salida, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.mcmeta", json.dumps({
            "pack": {"pack_format": 48, "description": DESCRIPCION}
        }, indent=2, ensure_ascii=False))

        for fichero in sorted((FUENTE / "spawn_pool_world").glob("*.json")):
            especie = especie_del_spawn(fichero, especies, indice)
            if especie is None:
                sin_resolver.append(fichero.name)
                continue
            if especie in nombres_activos:
                conservadas += 1
                continue

            # Sobrescritura minima: mismo nombre de fichero, apagado.
            z.writestr(
                f"data/cobblemon/spawn_pool_world/{fichero.name}",
                json.dumps({
                    "enabled": False,
                    "neededInstalledMods": [],
                    "neededUninstalledMods": [],
                    "spawns": []
                }, indent=2))
            apagadas += 1

    print(f"Spawns conservados:   {conservadas}")
    print(f"Spawns apagados:      {apagadas}")
    if sin_resolver:
        print(f"Sin resolver ({len(sin_resolver)}): {', '.join(sin_resolver[:8])}")
        print("  -> se dejan ACTIVOS a proposito: es mas seguro que un Pokemon")
        print("     de mas que uno de menos por un fallo de deteccion.")

    fugas = evoluciones_que_cruzan(especies, activas)
    print(f"\nEvoluciones que cruzan fuera del rango: {len(fugas)}")
    print("  NO se bloquean. Esas especies solo se consiguen evolucionando,")
    print("  lo que las convierte en las mas dificiles del servidor.")
    for origen, destino, gen in fugas:
        print(f"    {origen:<14} -> {destino:<14} ({gen})")

    print(f"\nDatapack: {zip_salida}  ({zip_salida.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
