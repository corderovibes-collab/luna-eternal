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

DE DONDE SALE LA LISTA

Del **modpack oficial de Cobblemon** (D-031). La lista vive en
`gen_modpack.py`, que descarga su `.mrpack`, lee el indice y aplica nuestras
exclusiones y anadidos. Aqui solo se traduce a formato de launcher. Ver ese
fichero para el porque de cada exclusion.

PERFILES

Un mismo manifiesto sirve para las dos formas de entrar:

  jugador       lo justo para jugar
  constructor   ademas Axiom, WorldEdit CUI y Litematica

Los ficheros marcados `profiles: ["constructor"]` los ignora quien juega
normal. Cambiar de perfil en el launcher instala o desinstala esas herramientas
sin tocar nada mas.

POR QUE NO SE REDISTRIBUYE NINGUN MOD AJENO

El manifiesto guarda **URL y hash**, no el jar. Cada mod se descarga de su canal
oficial (el CDN de Modrinth), asi que las licencias restrictivas de Sodium,
EntityCulling o Xaero's no nos afectan (D-008).

Los NUESTROS son la excepcion evidente: no estan en Modrinth, asi que el jar se
publica en el repositorio publico del pack y el manifiesto apunta ahi. Lo mismo
vale para los ficheros de configuracion, que son texto y no son de nadie.

Uso:
    python tools/gen_manifest.py                # genera en build/pack/
    python tools/gen_manifest.py --publicar     # ademas lo sube al repo publico
"""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import urllib.parse
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_modpack import (EXTRA_CONSTRUCTOR, EXTRA_JUGADOR,  # noqa: E402
                         IRIS_PROPERTIES, MC, SERVIDOR, SHADERS, base,
                         loader_estable, servers_dat, version_de)

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

VERSION_PACK = "0.2.0"

# MODS NUESTROS que van en el CLIENTE.
#
# `lunaneon` (D-029) es el unico, y no es opcional para nadie: sin el, la
# ciudadela entera se ve como cubos negros y morados de "textura ausente". No
# es un mod de adorno, es la mitad del decorado.
#
# `lunaeternal` NO esta aqui y no debe estarlo: es de servidor, y su jar lleva
# dentro la logica de economia y el conector de la base de datos. Lo unico que
# se reparte es contenido.
PROPIOS = [
    {"carpeta": "neon", "prefijo": "lunaneon"},
]


def jar_propio(carpeta: str, prefijo: str) -> Path:
    """El jar compilado de uno de nuestros mods.

    Se falla en vez de omitirlo: un manifiesto sin `lunaneon` deja a todo el
    mundo viendo la ciudadela con texturas ausentes, y eso es peor que no
    publicar nada.
    """
    libs = RAIZ / carpeta / "build" / "libs"
    candidatos = [j for j in libs.glob(f"{prefijo}-*.jar")
                  if not j.stem.endswith(("-sources", "-dev"))]
    if not candidatos:
        raise SystemExit(f"No hay jar de {prefijo} en {libs}.\n"
                         f"    cd {carpeta} && bash build.sh")
    return max(candidatos, key=lambda j: j.stat().st_mtime)


# Carpetas que el JUGADOR cura: puede meter sus propios shaders y paquetes de
# texturas ahi, y puede tocar los nuestros.
SUYAS = ("shaderpacks/", "resourcepacks/")


def marcar(entrada: dict) -> dict:
    """Marca `once` lo que cae en una carpeta del jugador.

    `once` = se escribe si falta y no se pisa nunca. Sin esto, a quien hubiera
    ajustado o sustituido un shader se lo revertiriamos en cada arranque.

    **No impide actualizar**: el nombre del fichero lleva la version, asi que
    subir Complementary a r5.9 es una ruta nueva que si se descarga, y la
    anterior desaparece por la via normal (deja de estar en el manifiesto). Lo
    unico que `once` evita es reescribir una ruta identica.

    Lo cazo una prueba del launcher que descarga el manifiesto EN VIVO
    (`tools/smoke-test.mjs`), no una revision a ojo.
    """
    if entrada["path"].startswith(SUYAS):
        entrada["once"] = True
    return entrada


def construir() -> dict:
    ficheros = []
    base_ficheros, overrides, z = base()

    # 1. La base: el pack oficial, con SUS versiones. Un `.mrpack` guarda
    #    sha1 y sha512; el launcher usa sha1.
    for f in base_ficheros:
        ficheros.append(marcar({
            "path": f["path"],
            "sha1": f["hashes"]["sha1"],
            "size": f["fileSize"],
            "url": f["downloads"][0],
        }))
    print(f"  base            {len(base_ficheros)} ficheros del pack oficial")

    # 2. Lo nuestro por encima.
    for slug in EXTRA_JUGADOR + EXTRA_CONSTRUCTOR:
        v = version_de(slug)
        if not v:
            raise SystemExit(f"{slug} no tiene version para {MC}. El manifiesto "
                             f"NO se genera a medias: un jugador con un mod "
                             f"menos no se puede conectar.")
        f = v["files"][0]
        entrada = {"path": f"mods/{f['filename']}", "sha1": f["hashes"]["sha1"],
                   "size": f["size"], "url": f["url"]}
        if slug in EXTRA_CONSTRUCTOR:
            entrada["profiles"] = ["constructor"]
        ficheros.append(entrada)
        perfil = "constructor" if slug in EXTRA_CONSTRUCTOR else "todos"
        print(f"  extra           {slug:<26} {v['version_number']:<22} {perfil}")

    # 3. Shaderpacks. Van por URL de Modrinth y NUNCA copiados: la licencia de
    #    Complementary (§1.2.d) prohibe expresamente servirlo por "direct file
    #    upload". Es el mismo motivo por el que tampoco se les cambia el nombre.
    for slug in SHADERS:
        v = version_de(slug, loader=None)
        if not v:
            raise SystemExit(f"{slug} no tiene version para {MC}")
        f = v["files"][0]
        ficheros.append(marcar({"path": f"shaderpacks/{f['filename']}",
                                "sha1": f["hashes"]["sha1"], "size": f["size"],
                                "url": f["url"]}))
        print(f"  shader          {slug:<26} {v['version_number']}")

    SALIDA.mkdir(parents=True, exist_ok=True)

    # 4. Nuestros jars.
    for entrada in PROPIOS:
        jar = jar_propio(entrada["carpeta"], entrada["prefijo"])
        datos = jar.read_bytes()
        # Se copia junto al manifiesto para que `publicar()` lo suba con el. Los
        # dos tienen que viajar juntos: un manifiesto que apunta a un jar que
        # todavia no esta publicado es un launcher que falla en casa de todos.
        (SALIDA / jar.name).write_bytes(datos)
        ficheros.append({
            "path": f"mods/{jar.name}",
            "sha1": hashlib.sha1(datos).hexdigest(),
            "size": len(datos),
            "url": f"{BASE_RAW}/mods/{jar.name}",
        })
        print(f"  {entrada['prefijo']:<15} {jar.name:<26} nuestro")

    # 5. La configuracion del pack oficial. Son 113 ficheros de texto, 143 KB
    #    en total, que afinan los mods. Van marcados `once`: se escriben si
    #    faltan y no se pisan nunca, para que si un jugador ajusta algo no se lo
    #    revertamos en la siguiente actualizacion.
    dir_cfg = SALIDA / "overrides"
    if dir_cfg.exists():
        shutil.rmtree(dir_cfg)
    for n in overrides:
        rel = n[len("overrides/"):]
        datos = z.read(n)
        destino = dir_cfg / rel
        destino.parent.mkdir(parents=True, exist_ok=True)
        destino.write_bytes(datos)
        ficheros.append({
            "path": rel,
            "sha1": hashlib.sha1(datos).hexdigest(),
            "size": len(datos),
            "url": f"{BASE_RAW}/overrides/{urllib.parse.quote(rel)}",
            "once": True,
        })
    print(f"  configuracion   {len(overrides)} ficheros del pack oficial")

    # 6. Ficheros nuestros que se escriben una vez y no se vuelven a tocar.
    #
    #    La lista de servidores va como `once`: si se reescribiera en cada
    #    arranque, borrariamos los servidores que el jugador haya anadido por su
    #    cuenta. Y los shaders llegan APAGADOS: en cuanto active uno, su
    #    eleccion sobrevive a todas las actualizaciones siguientes.
    for nombre, ruta, datos in (
            ("servers.dat", "servers.dat", servers_dat(*SERVIDOR)),
            ("iris.properties", "config/iris.properties",
             IRIS_PROPERTIES.encode("utf-8"))):
        (SALIDA / nombre).write_bytes(datos)
        ficheros.append({
            "path": ruta,
            "sha1": hashlib.sha1(datos).hexdigest(),
            "size": len(datos),
            "url": f"{BASE_RAW}/{nombre}",
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
    """Sube el manifiesto y todo lo que se sirve desde el repositorio publico.

    Se clona y se hace push en vez de usar la API de contenidos porque
    `servers.dat` es binario y la API obliga a ir fichero a fichero con base64 y
    el SHA anterior. Clonar es mas simple y falla de forma mas evidente.
    """
    clon = RAIZ / "build" / "pack-repo"
    if not (clon / ".git").exists():
        subprocess.run(["gh", "repo", "clone", REPO_PUBLICO, str(clon)], check=True)
    subprocess.run(["git", "-C", str(clon), "pull", "--quiet"], check=True)

    for nombre in ("manifest.json", "servers.dat", "iris.properties"):
        (clon / nombre).write_bytes((SALIDA / nombre).read_bytes())

    # La configuracion del pack oficial, entera y sustituyendo lo anterior: si
    # ellos quitan un fichero, aqui tambien tiene que desaparecer.
    if (clon / "overrides").exists():
        shutil.rmtree(clon / "overrides")
    shutil.copytree(SALIDA / "overrides", clon / "overrides")

    # Nuestros jars. Se borran antes los de versiones anteriores: si se dejaran,
    # el repositorio acumularia un jar por version para siempre, y `raw` sirve
    # cualquiera de ellos — con lo que un manifiesto viejo cacheado seguiria
    # funcionando y nadie se enteraria de que hay dos versiones en circulacion.
    mods = clon / "mods"
    mods.mkdir(exist_ok=True)
    vigentes = set()
    for entrada in PROPIOS:
        jar = jar_propio(entrada["carpeta"], entrada["prefijo"])
        (mods / jar.name).write_bytes(jar.read_bytes())
        vigentes.add(jar.name)
    for viejo in mods.glob("*.jar"):
        if viejo.name not in vigentes:
            viejo.unlink()
            print(f"  retirado {viejo.name}")

    subprocess.run(["git", "-C", str(clon), "add", "-A"], check=True)
    hay_cambios = subprocess.run(
        ["git", "-C", str(clon), "diff", "--cached", "--quiet"]).returncode != 0
    if not hay_cambios:
        print("  nada que publicar: el manifiesto no ha cambiado")
        return
    subprocess.run(["git", "-C", str(clon), "commit", "-m",
                    f"Pack {VERSION_PACK}: base en el modpack oficial de Cobblemon"],
                   check=True)
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
