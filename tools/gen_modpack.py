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
LOADER = "0.16.14"
SERVIDOR = ("§6PokeReport §f: §bLuna Eternal", "s12.mia.us.tarohosting.com:33043")

# Solo lo necesario. Cada mod de mas es RAM, un punto de fallo y un bloqueo
# potencial cuando salga MC 1.22 (principio P5).
SOLO_CLIENTE = {"sodium", "entityculling", "modmenu"}
MODS = ["cobblemon", "fabric-api", "sodium", "lithium",
        "ferrite-core", "entityculling", "modmenu"]


def api(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))


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


def main():
    ficheros, total = [], 0
    for slug in MODS:
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
        "name": "PokeReport: Luna Eternal",
        "summary": "Cliente oficial. Lo justo para jugar: 137 MB y 4 GB de RAM bastan.",
        "files": ficheros,
        "dependencies": {"minecraft": MC, "fabric-loader": LOADER}}

    SALIDA.mkdir(parents=True, exist_ok=True)
    destino = SALIDA / "PokeReport-LunaEternal-0.1.0.mrpack"
    with zipfile.ZipFile(destino, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json",
                   json.dumps(index, indent=2, ensure_ascii=False))
        z.writestr("overrides/servers.dat", servers_dat(*SERVIDOR))

    print(f"\n{destino}")
    print(f"{len(ficheros)} mods · descarga total {total // 1048576} MB")


if __name__ == "__main__":
    main()
