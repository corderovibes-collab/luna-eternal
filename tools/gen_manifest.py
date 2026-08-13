#!/usr/bin/env python3
"""
Genera el manifest.json que consume el launcher, y lo publica.

QUE ES ESTO, EN UNA FRASE

El manifiesto es **la lista de la compra del cliente**: que ficheros tiene que
haber, con que huella SHA1 y de donde se bajan. El launcher lo lee en cada
arranque, lo compara con lo que hay en disco y baja SOLO lo que no cuadre.

De ahi salen las dos propiedades que importan:

  * Anadir, quitar o actualizar un mod es **regenerar este fichero**. No hay que
    repartir un launcher nuevo ni pedirle nada a nadie: los jugadores lo reciben
    la proxima vez que le den a Jugar.
  * Un mod retirado del manifiesto **se desinstala solo** en el cliente.

PERFILES

Un mismo manifiesto sirve para las dos formas de entrar:

  jugador       lo justo para jugar
  constructor   ademas Axiom y WorldEdit CUI, para construir la ciudadela

Los ficheros marcados `profiles: ["constructor"]` los ignora quien juega
normal. Cambiar de perfil en el launcher instala o desinstala esas herramientas
sin tocar nada mas.

POR QUE NO SE REDISTRIBUYE NINGUN MOD

El manifiesto guarda **URL y hash**, no el jar. Cada mod se descarga de su canal
oficial (el CDN de Modrinth), asi que las licencias restrictivas de Sodium o
EntityCulling no nos afectan (D-008).

Uso:
    python tools/gen_manifest.py                # genera en build/pack/
    python tools/gen_manifest.py --publicar     # ademas lo sube al repo publico
"""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_modpack import MC, SERVIDOR, loader_estable, servers_dat, version_de  # noqa: E402

RAIZ = Path(__file__).resolve().parent.parent
SALIDA = RAIZ / "build" / "pack"

# Repositorio PUBLICO. Tiene que serlo: el launcher pide el manifiesto sin
# credenciales, y contra uno privado recibiria un 404 en casa de cada jugador.
REPO_PUBLICO = "corderovibes-collab/luna-eternal-pack"


def rama_por_defecto() -> str:
    """La rama del repositorio publico, CONSULTADA.

    Estaba escrita a mano como `main` y la de ese repositorio es `master`: el
    manifiesto se publicaba bien y el enlace daba 404. Es el mismo fallo que ya
    costo un pack que no arrancaba por la version del cargador escrita a mano.
    """
    try:
        r = subprocess.run(["gh", "api", f"repos/{REPO_PUBLICO}",
                            "--jq", ".default_branch"],
                           capture_output=True, text=True, check=True)
        return r.stdout.strip() or "main"
    except (subprocess.CalledProcessError, FileNotFoundError):
        # Sin `gh` no se puede preguntar. Se avisa en vez de adivinar en
        # silencio: un enlace equivocado aqui deja a todo el mundo sin pack.
        print("  AVISO: no se pudo consultar la rama por defecto, se asume 'main'")
        return "main"


RAMA = rama_por_defecto()
BASE_RAW = f"https://raw.githubusercontent.com/{REPO_PUBLICO}/{RAMA}"

VERSION_PACK = "0.1.0"

# Mods, y en que perfil aparece cada uno.
#
#   optional  el jugador puede desactivarlos en Ajustes ("mods extra de
#             optimizacion"). Cobblemon y Fabric API NUNCA pueden serlo: sin
#             ellos no se conecta.
#   profiles  ausente = para todos.
MODS = [
    {"slug": "cobblemon"},
    {"slug": "fabric-api"},
    {"slug": "sodium", "optional": True},
    {"slug": "lithium", "optional": True},
    {"slug": "ferrite-core", "optional": True},
    {"slug": "entityculling", "optional": True},
    {"slug": "modmenu"},
    # Solo constructor. Son ~50 MB de herramienta de desarrollo que un jugador
    # normal no usa jamas (P10), y ademas necesitan permiso en el servidor.
    {"slug": "worldedit-cui", "profiles": ["constructor"]},
    {"slug": "axiom", "profiles": ["constructor"]},
    # Litematica sirve para MEDIR y para superponer un plano sobre el terreno.
    # malilib es su dependencia y va antes a proposito: sin ella, Litematica no
    # arranca.
    #
    # OJO con el ajuste `easyPlace`: coloca los bloques del esquema solo, con
    # una precision y un ritmo que ningun humano tiene. Viene apagado y asi se
    # queda — en un servidor ajeno es lo que hace que te baneen, y aqui no hace
    # falta porque para eso ya esta Axiom.
    {"slug": "malilib", "profiles": ["constructor"]},
    {"slug": "litematica", "profiles": ["constructor"]},
]


def construir() -> dict:
    ficheros = []
    for entrada in MODS:
        slug = entrada["slug"]
        v = version_de(slug)
        if not v:
            raise SystemExit(
                f"{slug} no tiene version para {MC}. El manifiesto NO se genera a "
                f"medias: un jugador con un mod menos no se puede conectar.")
        f = v["files"][0]
        ficheros.append({
            "path": f"mods/{f['filename']}",
            "sha1": f["hashes"]["sha1"],
            "size": f["size"],
            "url": f["url"],
            **({"optional": True} if entrada.get("optional") else {}),
            **({"profiles": entrada["profiles"]} if entrada.get("profiles") else {}),
        })
        perfil = ",".join(entrada.get("profiles", ["todos"]))
        print(f"  {slug:<15} {v['version_number']:<22} {perfil}")

    # La lista de servidores va como `once`: se escribe si falta y no se pisa
    # nunca. Si se reescribiera en cada arranque, borrariamos los servidores que
    # el jugador haya anadido por su cuenta.
    dat = servers_dat(*SERVIDOR)
    SALIDA.mkdir(parents=True, exist_ok=True)
    (SALIDA / "servers.dat").write_bytes(dat)
    ficheros.append({
        "path": "servers.dat",
        "sha1": hashlib.sha1(dat).hexdigest(),
        "size": len(dat),
        "url": f"{BASE_RAW}/servers.dat",
        "once": True,
    })

    host, puerto = SERVIDOR[1].split(":")
    return {
        "packVersion": VERSION_PACK,
        "minecraft": MC,
        # Se consulta, no se escribe: Cobblemon exige un minimo y un numero a
        # mano caduca en silencio. `loader_estable()` ya lo comprueba.
        "fabricLoader": loader_estable(),
        "server": {"name": "PokeReport : Luna Eternal", "host": host, "port": int(puerto)},
        "files": ficheros,
    }


def publicar() -> None:
    """Sube manifest.json y servers.dat al repositorio publico.

    Se clona y se hace push en vez de usar la API de contenidos porque
    `servers.dat` es binario y la API obliga a ir fichero a fichero con base64 y
    el SHA anterior. Clonar es mas simple y falla de forma mas evidente.
    """
    clon = RAIZ / "build" / "pack-repo"
    if not (clon / ".git").exists():
        subprocess.run(["gh", "repo", "clone", REPO_PUBLICO, str(clon)], check=True)
    subprocess.run(["git", "-C", str(clon), "pull", "--quiet"], check=True)

    for nombre in ("manifest.json", "servers.dat"):
        (clon / nombre).write_bytes((SALIDA / nombre).read_bytes())

    subprocess.run(["git", "-C", str(clon), "add", "manifest.json", "servers.dat"], check=True)
    hay_cambios = subprocess.run(
        ["git", "-C", str(clon), "diff", "--cached", "--quiet"]).returncode != 0
    if not hay_cambios:
        print("  nada que publicar: el manifiesto no ha cambiado")
        return
    subprocess.run(["git", "-C", str(clon), "commit", "-m",
                    f"Pack {VERSION_PACK}: manifiesto regenerado"], check=True)
    subprocess.run(["git", "-C", str(clon), "push", "--quiet"], check=True)
    print(f"  publicado en {BASE_RAW}/manifest.json")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--publicar", action="store_true",
                    help="ademas de generarlo, subirlo al repositorio publico")
    args = ap.parse_args()

    print(f"MANIFIESTO DEL PACK  ·  Minecraft {MC}")
    manifiesto = construir()

    destino = SALIDA / "manifest.json"
    destino.write_text(json.dumps(manifiesto, indent=2, ensure_ascii=False),
                       encoding="utf-8")
    total = sum(f.get("size", 0) for f in manifiesto["files"]) // 1048576
    print(f"\n  -> {destino}  ({len(manifiesto['files'])} ficheros, {total} MB)")
    print(f"     Fabric Loader {manifiesto['fabricLoader']}")

    if args.publicar:
        publicar()
    else:
        print("\n  Para publicarlo:  python tools/gen_manifest.py --publicar")


if __name__ == "__main__":
    main()
