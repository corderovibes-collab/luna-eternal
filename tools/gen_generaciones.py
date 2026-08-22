#!/usr/bin/env python3
"""
Genera el datapack que limita el servidor a Kanto y Johto (D-017).

Hace DOS cosas, y las dos hacen falta:

  1. APAGA LOS SPAWNS de todo lo que no sea generacion 1 o 2, poniendo
     "enabled": false en cada fichero de spawn_pool_world.
  2. RECORTA LA POKEDEX para que no se vean las especies bloqueadas.

POR QUE ASI Y NO BORRANDO NADA
  - Es reversible: activar una generacion es regenerar con otro parametro.
  - No toca el jar de Cobblemon, que puede actualizarse por debajo.
  - Los datos siguen existiendo: modelos, movimientos y evoluciones estan ahi
    desde el primer dia, solo que esas especies no aparecen solas.

⚠⚠ NO BASTA CON LEER EL FUENTE DE COBBLEMON. HAY QUE MIRAR DENTRO DE LOS JARS.

  Esta es la leccion cara. La primera version solo leia `vendor/cobblemon`, y
  durante seis dias el datapack parecio correcto. El 2026-08-17 entro CobbleVerse
  (D-037) con mods que meten spawns SUYOS en el MISMO namespace
  `data/cobblemon/spawn_pool_world/`, que el generador no miraba:

      mega_showdown        24 spawns  Castform, Rotom, Rockruff, Orbeetle...
      cobblemon-additions   1 spawn   Hatenna, Hatterene, Liepard, Purrloin

  O sea 29 especies de Gen 3-8 apareciendo en un servidor que se anuncia como
  Kanto + Johto. Y no aviso nadie: el datapack se genera igual de bien, solo que
  incompleto. Por eso este script ahora ABORTA si no encuentra la carpeta de
  mods, en vez de generar un datapack con agujeros en silencio.

LO QUE **NO** SE APAGA: LAS EVOLUCIONES
  23 especies de Kanto/Johto evolucionan a generaciones posteriores. Apagar el
  spawn NO impide evolucionar, y eso es DELIBERADO: esas 23 pasan a existir
  unicamente por evolucion, nunca por aparicion natural. Es decir, se
  convierten en las especies mas dificiles del servidor, y solo las consigue
  quien sabe como.

  El caso que mejor lo resume: ursaring -> ursaluna exige LUNA LLENA de noche.
  Es exactamente el pilar del juego, y bloquearlo seria tirar identidad.

  SI SE AÑADEN A LA POKEDEX, en la dex de su preevolucion. Si no, el jugador
  consigue un Ursaluna y la Pokedex no lo reconoce: parece un fallo, no una
  recompensa.

LA POKEDEX: SE VACIA, NO SE BORRA
  Un datapack puede sobrescribir un fichero pero no eliminarlo, y la interfaz
  lista TODAS las dex cargadas sin filtrar las vacias (PokedexGUI.kt:173). Asi
  que las 9 regiones bloqueadas se quedan como pestañas VACIAS. Es feo y es lo
  unico que se puede hacer desde datos.

Uso:
    python tools/gen_generaciones.py                 # Kanto + Johto
    python tools/gen_generaciones.py 1 2 3           # anadir Hoenn
    python tools/gen_generaciones.py --mods RUTA     # otra carpeta de mods
"""
import json
import os
import sys
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
FUENTE = RAIZ / "vendor" / "cobblemon" / "common" / "src" / "main" / "resources" / "data" / "cobblemon"
SALIDA = RAIZ / "build" / "datapacks"

# Los jars instalados. Se leen para cazar los spawns que meten los mods por su
# cuenta; ver el aviso de la cabecera.
MODS_POR_DEFECTO = (Path(os.environ.get("APPDATA", "")) / "LunaEternal"
                    / "instances" / "Luna Eternal" / "minecraft" / "mods")

# Que dex corresponde a cada generacion activa. Solo estan las que Cobblemon
# trae como region propia; una generacion sin dex no se recorta.
DEX_DE_GENERACION = {
    "generation1": "kanto",
    "generation2": "johto",
    "generation3": "hoenn",
    "generation4": "sinnoh",
    "generation5": "unova",
    "generation6": "kalos",
    "generation7": "alola",
    "generation8": "galar",
    "generation9": "paldea",
}

DESCRIPCION = (
    "Luna Eternal: limita las apariciones naturales y la Pokedex a las "
    "generaciones activas. Las demas especies siguen existiendo y son "
    "alcanzables por evolucion."
)


def generaciones_activas(argv):
    nums = [a for a in argv[1:] if a.isdigit()]
    if not nums:
        nums = ["1", "2"]
    return {f"generation{n}" for n in nums}


def carpeta_mods(argv):
    if "--mods" in argv:
        return Path(argv[argv.index("--mods") + 1])
    return MODS_POR_DEFECTO


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


def especie_de_datos(datos, indice):
    """Deduce la especie mirando DENTRO del fichero de spawn."""
    for spawn in datos.get("spawns", []):
        pokemon = (spawn.get("pokemon") or "").split()
        if not pokemon:
            continue
        clave = indice.get(normalizar(pokemon[0]))
        if clave:
            return clave
    return None


def especie_de_nombre(nombre_fichero, indice):
    """Ultimo recurso: el nombre del fichero, tipo `0025_pikachu.json`."""
    partes = Path(nombre_fichero).stem.split("_", 1)
    if len(partes) == 2:
        return indice.get(normalizar(partes[1]))
    return None


def recolectar_spawns(mods, indice):
    """Todos los ficheros de spawn que el servidor va a cargar.

    Devuelve  ruta_datapack -> (especie, origen)

    Se juntan DOS fuentes porque ninguna sola es suficiente: el fuente clonado
    da los de Cobblemon con su ruta exacta, y los jars dan los que añaden los
    mods --que van al MISMO namespace y por eso el datapack puede taparlos con
    el mismo mecanismo, sin inventar nada.
    """
    encontrados = {}

    # 1. Cobblemon, desde el fuente clonado.
    for fichero in sorted((FUENTE / "spawn_pool_world").glob("*.json")):
        try:
            datos = json.loads(fichero.read_text(encoding="utf-8"))
        except Exception:
            continue
        especie = (especie_de_datos(datos, indice)
                   or especie_de_nombre(fichero.name, indice))
        ruta = f"data/cobblemon/spawn_pool_world/{fichero.name}"
        encontrados[ruta] = (especie, "cobblemon (fuente)")

    # 2. Lo que metan los mods. Un mod puede usar cualquier namespace, asi que
    #    se respeta la ruta tal cual viene: sobrescribir exige la MISMA ruta.
    for jar in sorted(mods.glob("*.jar")):
        try:
            z = zipfile.ZipFile(jar)
        except Exception:
            continue
        for nombre in z.namelist():
            if "spawn_pool_world" not in nombre or not nombre.endswith(".json"):
                continue
            if nombre in encontrados:
                continue          # ya lo tenemos del fuente, con mejor dato
            try:
                datos = json.loads(z.read(nombre).decode("utf-8"))
            except Exception:
                continue
            if datos.get("enabled") is False:
                continue          # el propio mod ya lo trae apagado
            especie = (especie_de_datos(datos, indice)
                       or especie_de_nombre(nombre, indice))
            encontrados[nombre] = (especie, jar.name)

    return encontrados


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


def entradas_de_dex_disponibles():
    """Los ids de entrada de Pokedex que existen de verdad.

    Se comprueba antes de referenciar ninguno porque `getEntries()` hace
    `mapNotNull`: un id que no existe se descarta EN SILENCIO, y entonces la
    especie no sale en la Pokedex sin que nada avise.
    """
    ids = set()
    raiz = FUENTE / "dex_entries" / "pokemon"
    for fichero in raiz.rglob("*.json"):
        try:
            datos = json.loads(fichero.read_text(encoding="utf-8"))
        except Exception:
            continue
        if datos.get("id"):
            ids.add(datos["id"])
    return ids


def escribir_dexes(z, especies, activas, fugas, disponibles):
    """Recorta la Pokedex a las generaciones activas.

    Tres movimientos:
      national  pasa a agregar SOLO las dex activas
      activas   conservan sus entradas + las evoluciones que salen del rango
      el resto  se quedan VACIAS (no se pueden borrar; ver la cabecera)
    """
    dexes = FUENTE / "dexes"
    if not dexes.exists():
        print("  AVISO: no hay carpeta dexes/ en el fuente. Pokedex sin tocar.")
        return 0, 0, []

    activas_dex = {DEX_DE_GENERACION[g] for g in activas if g in DEX_DE_GENERACION}

    # Las evoluciones que cruzan, agrupadas por la dex de su PREEVOLUCION: un
    # Ursaluna sale de un Ursaring de Johto, asi que aparece en la dex de Johto.
    extra = {}
    ausentes = []
    for origen, destino, _ in fugas:
        gen_origen = especies[origen][0]
        dex = DEX_DE_GENERACION.get(gen_origen)
        if dex not in activas_dex:
            continue
        id_entrada = f"cobblemon:{normalizar(destino)}"
        if id_entrada not in disponibles:
            ausentes.append(id_entrada)
            continue
        extra.setdefault(dex, [])
        if id_entrada not in extra[dex]:
            extra[dex].append(id_entrada)

    vaciadas, ampliadas = 0, 0
    for fichero in sorted(dexes.glob("*.json")):
        try:
            datos = json.loads(fichero.read_text(encoding="utf-8"))
        except Exception:
            continue
        nombre = fichero.stem
        ruta = f"data/cobblemon/dexes/{fichero.name}"

        if datos.get("type") == "cobblemon:aggregate_pokedex_def":
            # La national deja de agregarlo todo.
            datos["subDexIds"] = [s for s in datos.get("subDexIds", [])
                                  if s.split(":")[-1] in activas_dex]
            z.writestr(ruta, json.dumps(datos, indent=2, ensure_ascii=False))
            continue

        if nombre in activas_dex:
            nuevas = extra.get(nombre, [])
            if not nuevas:
                continue          # sin cambios: no hace falta sobrescribirla
            datos["entries"] = list(datos.get("entries", [])) + nuevas
            z.writestr(ruta, json.dumps(datos, indent=2, ensure_ascii=False))
            ampliadas += len(nuevas)
            continue

        # Region bloqueada: se vacia.
        datos["entries"] = []
        z.writestr(ruta, json.dumps(datos, indent=2, ensure_ascii=False))
        vaciadas += 1

    return vaciadas, ampliadas, ausentes


def main():
    argv = sys.argv
    activas = generaciones_activas(argv)
    mods = carpeta_mods(argv)

    # ABORTA en vez de generar un datapack incompleto. Ver la cabecera: esto es
    # exactamente lo que fallo en silencio durante seis dias.
    if not mods.exists():
        sys.exit(f"No encuentro la carpeta de mods: {mods}\n"
                 f"Sin ella NO se pueden cazar los spawns que añaden los mods, y\n"
                 f"el datapack saldria incompleto sin avisar. Pasa la ruta con\n"
                 f"    python tools/gen_generaciones.py --mods <RUTA>")

    especies = leer_especies()
    nombres_activos = {n for n, (g, _) in especies.items() if g in activas}
    indice = {normalizar(n): n for n in especies}
    disponibles = entradas_de_dex_disponibles()

    print(f"Generaciones activas: {', '.join(sorted(activas))}")
    print(f"Especies activas:     {len(nombres_activos)} de {len(especies)}")
    print(f"Mods leidos de:       {mods}")

    todos = recolectar_spawns(mods, indice)
    de_mods = {r for r, (_, o) in todos.items() if o != "cobblemon (fuente)"}
    print(f"Ficheros de spawn:    {len(todos)} "
          f"({len(de_mods)} los añaden los mods)")

    SALIDA.mkdir(parents=True, exist_ok=True)
    zip_salida = SALIDA / "luna-generaciones.zip"

    apagadas, conservadas, sin_resolver = 0, 0, []
    por_mod = {}

    with zipfile.ZipFile(zip_salida, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.mcmeta", json.dumps({
            "pack": {"pack_format": 48, "description": DESCRIPCION}
        }, indent=2, ensure_ascii=False))

        for ruta, (especie, origen) in sorted(todos.items()):
            if especie is None:
                sin_resolver.append(Path(ruta).name)
                continue
            if especie in nombres_activos:
                conservadas += 1
                continue

            # Sobrescritura minima: MISMA ruta, apagado.
            z.writestr(ruta, json.dumps({
                "enabled": False,
                "neededInstalledMods": [],
                "neededUninstalledMods": [],
                "spawns": []
            }, indent=2))
            apagadas += 1
            if origen != "cobblemon (fuente)":
                por_mod[origen] = por_mod.get(origen, 0) + 1

        fugas = evoluciones_que_cruzan(especies, activas)
        vaciadas, ampliadas, ausentes = escribir_dexes(
            z, especies, activas, fugas, disponibles)

    print(f"Spawns conservados:   {conservadas}")
    print(f"Spawns apagados:      {apagadas}")
    for mod, n in sorted(por_mod.items()):
        print(f"    de {mod}: {n}")
    if sin_resolver:
        print(f"Sin resolver ({len(sin_resolver)}): {', '.join(sin_resolver[:8])}")
        print("  -> se dejan ACTIVOS a proposito: es mas seguro que un Pokemon")
        print("     de mas que uno de menos por un fallo de deteccion.")

    print(f"\nPokedex:")
    print(f"  regiones vaciadas:  {vaciadas}")
    print(f"  entradas añadidas:  {ampliadas} (evoluciones fuera de rango)")
    if ausentes:
        print(f"  SIN entrada de dex ({len(ausentes)}): {', '.join(ausentes)}")
        print("     esas NO se podran registrar aunque se consigan.")

    print(f"\nEvoluciones que cruzan fuera del rango: {len(fugas)}")
    print("  NO se bloquean. Esas especies solo se consiguen evolucionando,")
    print("  lo que las convierte en las mas dificiles del servidor.")
    for origen, destino, gen in fugas:
        print(f"    {origen:<14} -> {destino:<14} ({gen})")

    print(f"\nDatapack: {zip_salida}  ({zip_salida.stat().st_size // 1024} KB)")
    print("  subirlo:  world/datapacks/  y reiniciar")


if __name__ == "__main__":
    main()
