#!/usr/bin/env python3
"""
Genera el pack de cliente (.mrpack) contra la API de Modrinth.

Nada se escribe a mano: actualizar versiones es volver a ejecutar esto.

El .mrpack NO redistribuye mods — guarda URLs y hashes, y el launcher los baja
de Modrinth. Por eso las licencias restrictivas de algunos (Sodium,
EntityCulling) no son un problema: se descargan de su canal oficial (D-008).
"""
import json, os, struct, urllib.request, zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SALIDA = RAIZ / "build"
UA = {"User-Agent": "PokeReport-LunaEternal/0.1 (dev)"}

MC = "1.21.1"
SERVIDOR = ("§6PokeReport §f: §bLuna Eternal", "s12.mia.us.tarohosting.com:33043")

# Version minima del cargador. Cobblemon 1.7.3 la exige y no arranca sin ella:
# "requires version 0.17.2 or later of mod 'Fabric Loader'". Estaba escrita a
# mano en 0.16.14 y el pack generado no arrancaba (2026-08-11).
LOADER_MINIMO = (0, 17, 2)

# Mods que el servidor no lleva: van marcados para que el launcher no intente
# instalarlos del lado equivocado.
SOLO_CLIENTE = {"sodium", "entityculling", "modmenu", "worldedit-cui"}

# Pack de JUGADOR: lo justo para jugar. Cada mod de mas es RAM, un punto de
# fallo y un bloqueo cuando salga MC 1.22 (P5).
MODS_JUGADOR = ["cobblemon", "fabric-api", "sodium", "lithium",
                "ferrite-core", "entityculling", "modmenu"]

# Pack de CONSTRUCTOR: lo anterior mas las herramientas de edicion. Son 45 MB
# de utilidad de desarrollo que un jugador normal no necesita para nada, asi
# que va en un pack aparte en vez de engordar el de todos.
MODS_CONSTRUCTOR = MODS_JUGADOR + [
    # Dibuja la seleccion de WorldEdit. Sin el se construye a ciegas: marcas
    # dos esquinas y no ves que has marcado.
    "worldedit-cui",
    # Editor de construccion. Ver docs/world/construccion.md §3-bis: en
    # multijugador hay que pedir whitelist gratis en su Discord.
    "axiom",
]


def api(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))


def loader_estable():
    """Ultima version estable del cargador de Fabric, consultada, no escrita.

    Se comprueba contra LOADER_MINIMO: si algun dia la ultima estable fuera
    mas antigua que lo que exige Cobblemon, es mejor fallar aqui que generar
    un pack que no arranca."""
    d = api("https://meta.fabricmc.net/v2/versions/loader")
    v = next(x["version"] for x in d if x.get("stable"))
    if tuple(int(n) for n in v.split(".")[:3]) < LOADER_MINIMO:
        raise SystemExit(f"Fabric Loader estable {v} < minimo "
                         f"{'.'.join(map(str, LOADER_MINIMO))} que pide Cobblemon")
    return v


def version_de(slug):
    d = api(f"https://api.modrinth.com/v2/project/{slug}/version"
            f"?loaders=%5B%22fabric%22%5D&game_versions=%5B%22{MC}%22%5D")
    # 'release' antes que beta: el cliente de un servidor no es sitio para
    # probar versiones inestables.
    estables = [v for v in d if v.get("version_type") == "release"]
    return (estables or d)[0] if d else None


def servers_dat(nombre, ip):
    """servers.dat es NBT SIN comprimir. Con el servidor ya en la lista, el
    jugador no tiene que escribir ninguna IP."""
    def cadena(t):
        b = t.encode("utf-8")
        return struct.pack(">H", len(b)) + b

    def etiqueta(tipo, nom, payload):
        return bytes([tipo]) + cadena(nom) + payload

    entrada = etiqueta(8, "name", cadena(nombre)) \
            + etiqueta(8, "ip", cadena(ip)) + b"\x00"
    lista = etiqueta(9, "servers", bytes([10]) + struct.pack(">i", 1) + entrada)
    return etiqueta(10, "", lista + b"\x00")


def construir(nombre_pack, mods, sufijo, resumen):
    ficheros, total = [], 0
    for slug in mods:
        v = version_de(slug)
        if not v:
            print(f"  AVISO: {slug} no tiene version para {MC}, se omite")
            continue
        f = v["files"][0]
        total += f["size"]
        ficheros.append({
            "path": f"mods/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": "required",
                    "server": "unsupported" if slug in SOLO_CLIENTE else "required"},
            "downloads": [f["url"]],
            "fileSize": f["size"]})
        print(f"  {slug:<15} {v['version_number']}")

    index = {
        "formatVersion": 1, "game": "minecraft", "versionId": "0.1.0",
        "name": nombre_pack,
        "summary": resumen,
        "files": ficheros,
        "dependencies": {"minecraft": MC, "fabric-loader": loader_estable()}}

    SALIDA.mkdir(parents=True, exist_ok=True)
    destino = SALIDA / f"PokeReport-LunaEternal{sufijo}-0.1.0.mrpack"
    with zipfile.ZipFile(destino, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json",
                   json.dumps(index, indent=2, ensure_ascii=False))
        z.writestr("overrides/servers.dat", servers_dat(*SERVIDOR))

    print(f"  -> {destino.name}  ({len(ficheros)} mods, {total // 1048576} MB)\n")


def main():
    print("PACK DE JUGADOR")
    construir("PokeReport: Luna Eternal", MODS_JUGADOR, "",
              "Cliente oficial. Lo justo para jugar: 4 GB de RAM bastan.")
    print("PACK DE CONSTRUCTOR")
    construir("PokeReport: Luna Eternal (Constructor)", MODS_CONSTRUCTOR,
              "-Constructor",
              "Como el oficial, mas WorldEdit CUI y Axiom para construir.")


if __name__ == "__main__":
    main()
