# -*- coding: utf-8 -*-
"""
GENERA EL DATAPACK DE LA LIGA NARANJA.

Los cinco lideres del Equipo Naranja --Cissy, Danny, Rudy, Luana y Drake-- NO
EXISTEN en ningun dato que tengamos. Se comprobo abriendo los dos ficheros:

    entrenadores  156 en COBBLEVERSE-RCT-DP-v20.zip, y no esta ninguno de ellos
                  (el unico Drake que hay es el Alto Mando de Hoenn)
    medallas      45 texturas en CobbleverseBadges-1.3.jar, ninguna suya

Asi que hay que crearlos. Esto escribe world/datapacks/luna-naranja.zip.

    python tools/gen_naranja.py            escribe el zip en build/
    python tools/gen_naranja.py --subir    y lo sube al servidor

⚠⚠⚠ EL EQUIPO DE ESTE FICHERO CASI NO SE USA, Y AUN ASI TIENE QUE ESTAR BIEN.

   Quien decide con que Pokemon pelea un lider es `Adaptador`, que compone el
   equipo EN CADA COMBATE contra lo que trae el jugador. Lo de aqui es el
   respaldo: lo que saldria si algun dia ese gimnasio se quedara sin repertorio.

   Pero el fichero SI hace falta por otra cosa: sin el, `Lideres.idValido()`
   dice que no y el reto contesta «sin lider». O sea que el gimnasio no existe.

⚠⚠ NO TIENEN SKIN, Y NO SE ROMPE NADA. rctmod busca la piel del entrenador en
   `assets/rctmod/textures/trainers/single/<id>.png` y, si no esta, usa
   `default.png` --que SI viene en su jar, comprobado--. Los cinco saldran con
   el aspecto generico hasta que alguien dibuje sus cinco pieles de 64x64.

⚠ `spawnWeightFactor` a 0: NO aparecen por el mundo. Un Drake de nivel 62
  paseando por una llanura seria el jefe final del servidor. Igual que Brock.
"""
import argparse
import io
import json
import pathlib
import sys
import zipfile

SALIDA = pathlib.Path("build/luna-naranja.zip")

# ⚠ El nivel es el del gimnasio, y sale de Gimnasio.java. Se repite aqui a
#   proposito y NO se lee de alla: son dos sistemas distintos --el nuestro y el
#   de rctmod-- y atarlos obligaria a parsear Java desde Python.
#   Lo que si hace el autotest es comprobar que el gimnasio existe.
NIVEL = {"cissy": 49, "danny": 52, "rudy": 55, "luana": 58, "drake": 62}

# El equipo de RESPALDO de cada uno: el del anime, hasta donde se puede.
# ⚠ Todas las especies y movimientos se validaron contra data/cobblemon/species/
#   del jar antes de escribirlos aqui, igual que los repertorios de Java.
EQUIPOS = {
    "cissy": [
        ("seadra",    "poisonpoint", "timid",   ["hydropump", "icebeam", "dragonpulse", "agility"]),
        ("starmie",   "illuminate",  "timid",   ["surf", "icebeam", "psychic", "recover"]),
        ("blastoise", "torrent",     "modest",  ["hydropump", "icebeam", "flashcannon", "rapidspin"]),
    ],
    "danny": [
        ("cloyster",  "shellarmor",  "adamant", ["iciclespear", "surf", "spikes", "irondefense"]),
        ("piloswine", "oblivious",   "adamant", ["icefang", "earthquake", "blizzard", "takedown"]),
        ("dewgong",   "thickfat",    "modest",  ["icebeam", "surf", "aurorabeam", "rest"]),
    ],
    "rudy": [
        ("raichu",     "static",     "jolly",   ["thunderbolt", "irontail", "quickattack", "thunderwave"]),
        ("magneton",   "magnetpull", "modest",  ["thunderbolt", "flashcannon", "thunderwave", "lightscreen"]),
        ("electabuzz", "static",     "timid",   ["thunderbolt", "thunderpunch", "icepunch", "psychic"]),
    ],
    "luana": [
        ("ninetales", "flashfire",   "timid",   ["flamethrower", "confuseray", "willowisp", "energyball"]),
        ("magmar",    "flamebody",   "modest",  ["flamethrower", "thunderpunch", "confuseray", "psychic"]),
        ("arcanine",  "intimidate",  "adamant", ["flamethrower", "extremespeed", "crunch", "willowisp"]),
    ],
    # ⚠⚠ El campeon lleva SEIS: en el anime su combate es un 6 contra 6 completo.
    #    Falta Ditto, que es su primero en la serie: solo aprende Transformacion
    #    y aqui cada Pokemon necesita cuatro ataques.
    "drake": [
        ("onix",       "rockhead",   "impish",  ["rockslide", "earthquake", "irontail", "dragonbreath"]),
        ("gengar",     "cursedbody", "timid",   ["shadowball", "sludgebomb", "thunderbolt", "hypnosis"]),
        ("venusaur",   "overgrow",   "bold",    ["gigadrain", "sludgebomb", "sleeppowder", "synthesis"]),
        ("electabuzz", "static",     "timid",   ["thunderbolt", "thunderpunch", "icepunch", "psychic"]),
        ("machamp",    "guts",       "adamant", ["crosschop", "earthquake", "rockslide", "bulkup"]),
        ("dragonite",  "innerfocus", "adamant", ["outrage", "dragondance", "firepunch", "extremespeed"]),
    ],
}

NOMBRES = {"cissy": "Cissy", "danny": "Danny", "rudy": "Rudy",
           "luana": "Luana", "drake": "Drake"}

# ⚠ El `type` de rctmod da el simbolo y el color del entrenador, no su piel.
#   `leader` y `champ` ya existen en su datapack: no inventamos ninguno.
TIPO_MOB = {"cissy": "leader", "danny": "leader", "rudy": "leader",
            "luana": "leader", "drake": "champ"}


def equipo(gid):
    """El equipo en el formato de rctmod."""
    nivel = NIVEL[gid]
    salida = []
    for especie, habilidad, naturaleza, movimientos in EQUIPOS[gid]:
        salida.append({
            "species": especie,
            "level": nivel,
            "nature": naturaleza,
            "ability": habilidad,
            "moveset": movimientos,
            # ⚠ IVs a 31 como todos los lideres del datapack oficial. Un lider
            #   con IVs mediocres se nota: pierde carreras de velocidad que
            #   deberia ganar, y eso parece que la IA juega mal.
            "ivs": {"hp": 31, "atk": 31, "def": 31, "spa": 31, "spd": 31, "spe": 31},
            "evs": {"hp": 100, "spe": 40},
        })
    return salida


def trainer(gid):
    return {
        "name": {"literal": NOMBRES[gid]},
        # La IA de aqui es la de rctmod y solo vale para el respaldo: cuando
        # manda `Adaptador`, la IA la pone el (StrongBattleAI con su pericia).
        "ai": {"type": "rct", "data": {
            "moveBias": 1, "switchBias": 0.6, "statMoveBias": 1,
            "itemBias": 0.9, "maxSelectMargin": 0.1}},
        "battleRules": {"maxItemUses": 3},
        "bag": [{"item": "cobblemon:full_restore", "quantity": 3}],
        "team": equipo(gid),
        "battleFormat": "GEN_9_SINGLES",
    }


def mob(gid):
    return {
        "series": ["naranja"],
        "type": TIPO_MOB[gid],
        "maxTrainerWins": -1,
        "maxTrainerDefeats": 1,
        "battleCooldownTicks": 240,
        # ⚠⚠ CERO: no aparece solo por el mundo. Lo coloca nuestro codigo en su
        #    sala y en la ciudadela, y en ningun otro sitio.
        "spawnWeightFactor": 0,
    }


def construir():
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.mcmeta", json.dumps({
            "pack": {
                "pack_format": 48,
                "description": "Luna Eternal - Liga Naranja (5 lideres)",
            }
        }, indent=2))
        for gid in EQUIPOS:
            tid = "naranja_" + gid
            z.writestr("data/rctmod/trainers/%s.json" % tid,
                       json.dumps(trainer(gid), indent=2))
            z.writestr("data/rctmod/mobs/trainers/single/%s.json" % tid,
                       json.dumps(mob(gid), indent=2))
    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.write_bytes(buf.getvalue())
    return buf.getvalue()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--subir", action="store_true",
                    help="sube el zip a world/datapacks del servidor")
    args = ap.parse_args()

    datos = construir()
    print("  %s  (%d entrenadores, %d bytes)"
          % (SALIDA, len(EQUIPOS), len(datos)))
    for gid in EQUIPOS:
        print("     naranja_%-6s nivel %-3d %d Pokemon"
              % (gid, NIVEL[gid], len(EQUIPOS[gid])))

    if not args.subir:
        print("\n  Para instalarlo:  python tools/gen_naranja.py --subir")
        print("  OJO: un datapack se lee AL ARRANCAR: hace falta reiniciar.")
        return

    sys.path.insert(0, str(pathlib.Path(__file__).parent))
    import ptero
    ptero.subir("/world/datapacks", SALIDA.name, datos)
    print("\n  subido a /world/datapacks/%s" % SALIDA.name)
    print("  OJO: un datapack se lee AL ARRANCAR: hace falta reiniciar.")


if __name__ == "__main__":
    main()
