#!/usr/bin/env python3
"""Publica SOLO el JAR de Luna Eternal sobre el manifiesto vivo del launcher.

Se usa cuando el generador integral detecta cambios de pack no relacionados.
No reconstruye, elimina ni cambia ninguna otra entrada del manifiesto.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import sys
import urllib.request
import argparse
import base64
import subprocess
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_manifest as manifest  # noqa: E402


def descargar_json(url: str) -> dict:
    request = urllib.request.Request(url, headers={"User-Agent": "luna-eternal"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, help="JAR validado; permite publicar un parche dirigido sobre el JAR vivo")
    parser.add_argument("--asset-ya-subido", action="store_true",
                        help="solo mueve el manifiesto tras verificar el activo")
    parser.add_argument("--retirar-almacenamiento", action="store_true",
                        help="retira Sophisticated Storage y Tom's Storage del manifiesto vivo")
    parser.add_argument("--incluir-punchy", action="store_true",
                        help="anade Punchy 2.8c Fabric 1.21.1 solo al launcher")
    args = parser.parse_args()
    jar = args.jar.resolve() if args.jar else RAIZ / "mod" / "build" / "libs" / "lunaeternal-0.1.0.jar"
    if not jar.is_file():
        raise SystemExit(f"No existe el JAR validado: {jar}")
    data = jar.read_bytes()
    sha1 = hashlib.sha1(data).hexdigest()
    name = manifest.publicado(jar, sha1)

    pointer = descargar_json(manifest.URL_PUNTERO)
    live = descargar_json(pointer["manifest"])
    retirados = []
    if args.retirar_almacenamiento:
        nombres = {
            "sophisticatedstorage-1.21.1-1.3.7.9.139.jar",
            "toms_storage_fabric-1.21-2.4.2.jar",
        }
        antes = len(live.get("files", []))
        retirados = [f["path"] for f in live.get("files", [])
                     if Path(f.get("path", "")).name in nombres]
        if len(retirados) != len(nombres):
            raise SystemExit(
                "El manifiesto vivo no contiene exactamente los dos JAR esperados: "
                + ", ".join(retirados))
        live["files"] = [f for f in live["files"]
                         if Path(f.get("path", "")).name not in nombres]
        assert len(live["files"]) == antes - 2
    punchy_agregado = False
    if args.incluir_punchy:
        punchy = {
            "path": "mods/punchy-2.8c-fabric-1.21.1.jar",
            "sha1": "fcca6d5503597d1175fa9b0d0cae55393f3a214c",
            "size": 1858121,
            "url": "https://cdn.modrinth.com/data/8aoMKplv/versions/80B8c9Qd/punchy-2.8c-fabric-1.21.1.jar",
        }
        existentes = [f for f in live.get("files", [])
                      if Path(f.get("path", "")).name.startswith("punchy-")]
        if existentes:
            if len(existentes) != 1 or existentes[0].get("sha1") != punchy["sha1"]:
                raise SystemExit("El manifiesto ya contiene una version distinta de Punchy")
        else:
            punchy["urls"] = [punchy["url"]]
            live["files"].append(punchy)
            punchy_agregado = True
    matches = [f for f in live.get("files", [])
               if f.get("path", "").startswith("mods/lunaeternal-")]
    if len(matches) != 1:
        raise SystemExit(f"Se esperaba un solo Luna Eternal vivo; encontrados {len(matches)}")

    asset = manifest.SALIDA / name
    if args.asset_ya_subido:
        listed = subprocess.check_output([
            "gh", "release", "view", manifest.TAG_ACTIVOS,
            "--repo", manifest.REPO_PUBLICO, "--json", "assets"], text=True)
        metadata = next((a for a in json.loads(listed)["assets"] if a["name"] == name), None)
        if metadata is None or metadata.get("size") != len(data):
            raise SystemExit("El activo remoto no existe o no coincide en tamaño")
    else:
        asset.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(jar, asset)
        manifest.subir_activos([asset])

    old = matches[0]
    replacement = {
        "path": old["path"], "sha1": sha1, "size": len(data),
        "url": f"{manifest.BASE_ACTIVOS}/{name}",
        "urls": [f"{manifest.BASE_ACTIVOS}/{name}"],
    }
    index = live["files"].index(old)
    live["files"][index] = replacement
    stamp = manifest.publicar_puntero(live)
    # The Qt launcher falls back to master/manifest.json when GitHub releases
    # are unreachable. Leaving it stale silently reinstalls the broken client.
    endpoint = f"repos/{manifest.REPO_PUBLICO}/contents/manifest.json"
    metadata = json.loads(subprocess.check_output(
        ["gh", "api", endpoint + "?ref=master"], text=True))
    fallback = json.loads(base64.b64decode(metadata["content"]))
    fallback_matches = [f for f in fallback["files"] if f.get("path", "").startswith("mods/lunaeternal-")]
    if len(fallback_matches) != 1:
        raise SystemExit("El respaldo debe contener exactamente un JAR Luna Eternal")
    fallback["files"][fallback["files"].index(fallback_matches[0])] = replacement
    payload = {
        "message": "Update Luna Eternal client in launcher fallback",
        "sha": metadata["sha"], "branch": "master",
        "content": base64.b64encode(json.dumps(fallback, indent=2).encode()).decode(),
    }
    payload_file = manifest.SALIDA / "luna-fallback-update.json"
    payload_file.write_text(json.dumps(payload), encoding="utf-8")
    subprocess.run(["gh", "api", "--method", "PUT", endpoint,
                    "--input", str(payload_file)], check=True, stdout=subprocess.DEVNULL)
    print("RESPALDO sincronizado: solo Luna Eternal; otras entradas conservadas.")
    print(f"PUBLICADO solo Luna Eternal: {old['sha1'][:10]} -> {sha1[:10]}")
    if retirados:
        print("RETIRADOS: " + ", ".join(retirados))
    if punchy_agregado:
        print("ANADIDO: mods/punchy-2.8c-fabric-1.21.1.jar (solo cliente)")
    print(f"MANIFIESTO {stamp}; el resto de {len(live['files']) - 1} entradas no cambió.")


if __name__ == "__main__":
    main()
