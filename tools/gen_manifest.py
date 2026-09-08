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
import time
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_modpack  # noqa: E402
from gen_modpack import (EXTRA_CONSTRUCTOR, EXTRA_JUGADOR,  # noqa: E402
                         IRIS_PROPERTIES, MC, SERVIDOR, SHADERS, base,
                         loader_estable, servers_dat, verificar_dependencias,
                         version_de)

RAIZ = Path(__file__).resolve().parent.parent
SALIDA = RAIZ / "build" / "pack"

# Repositorio PUBLICO. Tiene que serlo: el launcher pide el manifiesto sin
# credenciales, y contra uno privado recibiria un 404 en casa de cada jugador.
REPO_PUBLICO = "corderovibes-collab/luna-eternal-pack"

# ---------------------------------------------------------------------------
# LOS FICHEROS PESADOS NO SE SIRVEN DESDE `raw`
#
# `raw.githubusercontent` NO es un CDN de distribucion: limita por peticiones y
# contesta 429 y 503 cuando le llegan muchas. Con 117 de las 199 entradas
# apuntando ahi, un jugador que instalaba de cero se llevaba un
# "HTTP 429 en .../manifest.json" y no podia jugar -- mientras que a quien ya lo
# tenia todo bajado no le pasaba nada, porque no pedia nada.
#
# Los ficheros de los que somos duenos pasan a una RELEASE, que GitHub sirve por
# su CDN de descargas y sin ese limite.
#
# ⚠ VA MARCADA COMO PRERELEASE, y no es un detalle: el autoactualizador del
# launcher mira "la ultima release", y una release normal aqui le haria creer
# que hay un launcher nuevo. `autoUpdater.allowPrerelease` solo se enciende en
# versiones alpha/beta/rc, asi que una prerelease le resulta invisible.
TAG_ACTIVOS = "pack-assets"
BASE_ACTIVOS = f"https://github.com/{REPO_PUBLICO}/releases/download/{TAG_ACTIVOS}"

# ---------------------------------------------------------------------------
# EL PUNTERO: LO QUE HACE POSIBLE VOLVER ATRAS
#
# `manifest.json` se sobrescribia en cada publicacion. Eso significa que una
# publicacion mala rompe a TODO EL MUNDO A LA VEZ y no hay marcha atras: hay que
# regenerar y volver a publicar los 185 MB, con el pack roto mientras tanto.
#
# Ahora el manifiesto se publica CON SU HUELLA EN EL NOMBRE y no se toca jamas
# (`manifest-a1b2c3d4e5.json`), y lo que el launcher lee primero es un puntero
# de ~250 bytes que dice cual es el bueno.
#
#   volver atras = subir un fichero de 250 bytes    (`--volver-a <huella>`)
#   en vez de    = regenerar y republicar 185 MB
#
# Va en una release SUYA y no junto a los activos porque son dos cosas
# opuestas: `pack-assets` ACUMULA y nada se borra nunca; `pack-manifest` tiene
# un solo fichero que se reescribe. Mezclarlas hace que `--clobber` sobre la
# release de activos sea rutina, y ahi es justo donde no debe serlo.
#
# ⚠ TAMBIEN VA COMO PRERELEASE. El autoactualizador del launcher mira "la
#   ultima release" del repositorio, y una release normal aqui le haria creer
#   que hay un launcher nuevo.
TAG_PUNTERO = "pack-manifest"
BASE_PUNTERO = f"https://github.com/{REPO_PUBLICO}/releases/download/{TAG_PUNTERO}"
URL_PUNTERO = f"{BASE_PUNTERO}/latest.json"


def zip_carpeta(entradas: list, prefijo: str) -> bytes:
    """Empaqueta una carpeta de overrides en un zip, con rutas relativas a ella.

    DETERMINISTA A PROPOSITO: fecha fija, orden fijo y sin compresion variable.
    Si el zip cambiara de huella en cada ejecucion, el launcher se lo bajaria
    entero en cada actualizacion del pack aunque no hubiera cambiado ni un byte.
    """
    import io
    import zipfile
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for rel, datos in sorted(entradas):
            info = zipfile.ZipInfo(rel[len(prefijo):],
                                   date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, datos)
    return buf.getvalue()


def subir_activos(rutas: list) -> None:
    """Sube a la release de activos, creandola si no existe.

    `--clobber` porque los nombres llevan la huella dentro: si ya esta subido
    ese mismo fichero, volver a subirlo no cambia nada, y si es uno nuevo se
    anade. La release acumula, y eso es DELIBERADO: un manifiesto viejo que
    alguien tenga cacheado sigue encontrando sus ficheros.
    """
    existe = subprocess.run(
        ["gh", "release", "view", TAG_ACTIVOS, "--repo", REPO_PUBLICO],
        capture_output=True, text=True).returncode == 0
    if not existe:
        subprocess.run(
            ["gh", "release", "create", TAG_ACTIVOS, "--repo", REPO_PUBLICO,
             "--prerelease", "--title", "Ficheros del pack",
             "--notes", "Ficheros que sirve el launcher. NO es una version del "
                        "launcher: va marcada como prerelease justo para que el "
                        "autoactualizador la ignore."],
            check=True)
    subprocess.run(["gh", "release", "upload", TAG_ACTIVOS, *map(str, rutas),
                    "--repo", REPO_PUBLICO, "--clobber"], check=True)
    print(f"  activos         {len(rutas)} subidos a la release {TAG_ACTIVOS}")


def _release(tag: str, titulo: str, notas: str) -> None:
    """Se asegura de que exista la release, sin tocarla si ya esta."""
    existe = subprocess.run(["gh", "release", "view", tag, "--repo", REPO_PUBLICO],
                            capture_output=True, text=True).returncode == 0
    if not existe:
        subprocess.run(["gh", "release", "create", tag, "--repo", REPO_PUBLICO,
                        "--prerelease", "--title", titulo, "--notes", notas],
                       check=True)


def publicar_puntero(manifiesto: dict) -> str:
    """Publica el manifiesto con su huella y hace que `latest.json` lo señale.

    ⚠ SE LLAMA DESPUES DE SUBIR LOS ACTIVOS, Y EL ORDEN NO ES NEGOCIABLE.
      El puntero es lo unico que el launcher lee para saber que instalar. Si se
      actualizara antes que los ficheros que anuncia, habria una ventana --la
      que tarde la subida de 185 MB-- en la que cualquiera que le diera a Jugar
      se llevaria un 404 a mitad de descarga. Al reves no hay ventana: un activo
      que ya esta subido y que todavia nadie referencia no le hace daño a nadie.

    Devuelve la huella, que es lo que hace falta para `--volver-a`.
    """
    crudo = json.dumps(manifiesto, indent=2, ensure_ascii=False).encode("utf-8")
    huella = hashlib.sha1(crudo).hexdigest()
    nombre = f"manifest-{huella[:10]}.json"
    (SALIDA / nombre).write_bytes(crudo)

    # El manifiesto va con los activos: es inmutable igual que ellos, y asi
    # comparte la regla de que nada se borra nunca.
    subprocess.run(["gh", "release", "upload", TAG_ACTIVOS, str(SALIDA / nombre),
                    "--repo", REPO_PUBLICO, "--clobber"], check=True)

    _apuntar_a(huella[:10], len(crudo), huella)
    return huella[:10]


def _apuntar_a(corta: str, tamano: int, sha1: str) -> None:
    """Reescribe `latest.json`. Es lo unico mutable de toda la publicacion."""
    puntero = {
        "packVersion": VERSION_PACK,
        "manifest": f"{BASE_ACTIVOS}/manifest-{corta}.json",
        # El launcher comprueba la huella del manifiesto ANTES de fiarse de el.
        # Sin esto, quien pueda inyectarle un JSON le elige las URL de descarga
        # de los 185 MB, y con ellas lo que se ejecuta en su maquina.
        "sha1": sha1,
        "size": tamano,
        "publicado": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    ruta = SALIDA / "latest.json"
    ruta.write_text(json.dumps(puntero, indent=2), encoding="utf-8")
    _release(TAG_PUNTERO, "Puntero del pack",
             "Un solo fichero: `latest.json`, que dice cual es el manifiesto "
             "vigente. Volver atras es reescribirlo. Prerelease para que el "
             "autoactualizador del launcher lo ignore.")
    subprocess.run(["gh", "release", "upload", TAG_PUNTERO, str(ruta),
                    "--repo", REPO_PUBLICO, "--clobber"], check=True)
    print(f"  puntero         latest.json -> manifest-{corta}.json")


def volver_a(corta: str) -> None:
    """Vuelve el pack a un manifiesto anterior. Sube 250 bytes y ya esta.

    Esto es lo que antes no existia: una publicacion mala rompia a todo el mundo
    a la vez y la unica salida era regenerar y republicar los 185 MB, con el
    pack roto mientras tanto. Los manifiestos viejos siguen todos en la release
    de activos, asi que volver es senalar a otro.

        gh release view pack-assets --repo <repo>   # ver las huellas que hay
        python tools/gen_manifest.py --volver-a a1b2c3d4e5
    """
    url = f"{BASE_ACTIVOS}/manifest-{corta}.json"
    print(f"VOLVER A  ·  {url}")
    try:
        crudo = urllib.request.urlopen(
            urllib.request.Request(url, headers={"User-Agent": "luna-eternal"})).read()
    except OSError as e:
        raise SystemExit(f"  ese manifiesto no existe en la release ({e}).\n"
                         f"    gh release view {TAG_ACTIVOS} --repo {REPO_PUBLICO}")
    # Se comprueba que sea un manifiesto de verdad antes de apuntar a el: hacer
    # que todo el mundo lea un fichero roto es exactamente lo que se intenta
    # arreglar.
    datos = json.loads(crudo.decode("utf-8"))
    if not isinstance(datos.get("files"), list) or not datos["files"]:
        raise SystemExit("  ese fichero no trae lista de ficheros: no se apunta ahi.")
    _apuntar_a(corta, len(crudo), hashlib.sha1(crudo).hexdigest())
    print(f"  hecho: {len(datos['files'])} ficheros, pack {datos.get('packVersion')}")


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
        if r.stdout.strip():
            return r.stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        pass

    # SEGUNDO INTENTO, SIN `gh`. El 2026-08-21, con el PC recien formateado, `gh`
    # no estaba -- y esta funcion contestaba `main` tan tranquila. `git` habla
    # con el mismo servidor y casi siempre esta.
    try:
        r = subprocess.run(["git", "ls-remote", "--symref",
                            f"https://github.com/{REPO_PUBLICO}", "HEAD"],
                           capture_output=True, text=True, check=True, timeout=30)
        for linea in r.stdout.splitlines():
            # ref: refs/heads/master\tHEAD
            if linea.startswith("ref:") and "\tHEAD" in linea:
                return linea.split()[1].rsplit("/", 1)[-1]
    except (subprocess.CalledProcessError, FileNotFoundError, subprocess.TimeoutExpired):
        pass

    # ⚠⚠ EL ULTIMO RECURSO ES `master`, NO `main`, Y ESO NO ES UN DETALLE.
    #
    # Aqui ponia `main`, que es la costumbre general y NO la de este repositorio.
    # O sea que el respaldo era exactamente el valor equivocado: sin `gh`, esta
    # funcion devolvia con toda naturalidad la rama que da 404, y el aviso decia
    # "se asume 'main'" como si eso fuera prudente. El manifiesto se publica
    # bien, los enlaces apuntan a la nada, y el launcher no tiene forma de
    # explicarlo -- es el fallo que este mismo docstring dice haber costado ya
    # una vez.
    #
    # Adivinar sigue estando mal; adivinar el valor que sabemos que es falso
    # esta peor. Si algun dia el repositorio se renombra a `main`, los dos
    # intentos de arriba lo diran.
    print(f"  AVISO: no se pudo consultar la rama por defecto ni con `gh` ni con `git`.")
    print(f"         Se usa 'master', que es la de {REPO_PUBLICO}. COMPRUEBALO"
          f" antes de publicar.")
    return "master"


RAMA = rama_por_defecto()
BASE_RAW = f"https://raw.githubusercontent.com/{REPO_PUBLICO}/{RAMA}"

VERSION_PACK = "0.2.0"

# MODS NUESTROS que van en el CLIENTE.
#
# `lunaneon` (D-029) es el unico, y no es opcional para nadie: sin el, la
# ciudadela entera se ve como cubos negros y morados de "textura ausente". No
# es un mod de adorno, es la mitad del decorado.
#
# `lunaeternal` TAMBIEN va al cliente desde que existe el PokePad (D-025): la
# interfaz vive en su conjunto de fuentes `client`, y sin el jar no hay
# pantalla que abrir.
#
# Su jar lleva dentro la logica de economia y el conector de MariaDB, que un
# cliente no usa. Es el precio aceptado de D-025 y no es un descuido:
# separarlo en dos mods obligaria a duplicar los tipos que viajan entre
# cliente y servidor, y eso se desincroniza a la primera. Credenciales NO
# lleva: se leen de `config/` en el servidor y nunca entran en el jar.
#
# ⚠⚠⚠ `cobblemon-cards` NO ES NUESTRO, PERO VIAJA COMO SI LO FUERA, y el motivo
# importa: es una version PARCHEADA (CC0, `cards/parchear.py`). El jar de
# Modrinth NO SIRVE -- su release publicado no tiene `enableCardStats`, que es
# el interruptor que impide que una carta de pago de daño, armadura y x100 de
# apariciones. Comprobado con `javap` sobre el jar del CDN: nueve campos de
# configuracion frente a cuarenta y cuatro.
#
# Al parchearlo dejamos de poder servirlo desde el CDN de Modrinth --seria otro
# fichero con el mismo nombre-- asi que va a NUESTRA release, con su huella,
# igual que los dos mods propios.
#
# ⚠⚠⚠ Y AQUI ESTUVO EL FALLO DEL 2026-09-03, QUE DEJO EL SERVIDOR INACCESIBLE.
#    Esta entrada no existia, asi que una republicacion rutinaria --solo cambiaba
#    nuestro jar-- lo dejo fuera SIN DECIR NADA. El servidor si lo tiene y manda
#    sus 89 entradas de registro; el cliente que no lo tiene NO ENTRA:
#
#      «Se han recibido 89 entradas de registro desconocidas para este
#       cliente ... cobblemon-cards»
#
#    Es la regla de `trinkets` --un mod que registra algo que se sincroniza
#    tiene que estar en los DOS lados-- mordiendo por el otro extremo: no por
#    añadirlo al servidor, sino por QUITARLO del cliente. Lo que impide que
#    vuelva a pasar es `comprobar_bajas()`, mas abajo.
PROPIOS = [
    {"carpeta": "neon", "prefijo": "lunaneon"},
    {"carpeta": "mod", "prefijo": "lunaeternal"},
    {"carpeta": "cards", "prefijo": "cobblemon-cards"},
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


# ---------------------------------------------------------------------------
# NINGUN FICHERO DEPENDE DE UN SOLO ORIGEN
#
# Cada entrada llevaba UNA url. Si ese origen cae, cae el pack entero y no hay
# nada que hacer salvo esperar. Ahora lleva `urls`, en orden de preferencia, y
# el launcher va probando hasta que uno conteste.
#
# ⚠ `url` (singular) SE MANTIENE, y no es redundante: los launchers ya
#   instalados leen ese campo. Quitarlo dejaria sin pack a todo el que no se
#   haya actualizado todavia, que es justo quien no puede actualizarse porque
#   el pack no le baja. Se retira cuando no quede nadie en 1.0.x.
#
# Hoy la lista tiene un solo origen real para los activos porque no hay segundo
# CDN gratuito que sirva ficheros de 129 MB. El dia que se abra un bucket
# (Cloudflare R2 da 10 GB y egreso a coste cero; Bunny cobra ~0,01 $/GB), es
# ANADIR UNA CADENA AQUI y republicar — el launcher ya sabe recorrer la lista.
ESPEJOS_ACTIVOS: list = []


def con_espejos(entrada: dict) -> dict:
    """Convierte `url` en `urls`, con los espejos que haya para ese origen."""
    urls = [entrada["url"]]
    # `espejo` es de uso interno del generador: se consume aqui y no viaja al
    # manifiesto, donde solo tiene sentido la lista ya montada.
    suyo = entrada.pop("espejo", None)
    if suyo:
        urls.append(suyo)
    # Solo se espejan LOS FICHEROS NUESTROS. Los de Modrinth y Mojang ya salen
    # de sus propios CDN y copiarlos ni es nuestro trabajo ni esta permitido por
    # todas sus licencias (D-030 y el caso de los shaders).
    if entrada["url"].startswith(BASE_ACTIVOS):
        urls += [entrada["url"].replace(BASE_ACTIVOS, base.rstrip("/"))
                 for base in ESPEJOS_ACTIVOS]
    entrada["urls"] = urls
    return entrada


def publicado(jar: Path, sha1: str) -> str:
    """El nombre con el que un jar NUESTRO se publica: lleva su huella dentro.

    `raw.githubusercontent.com` cachea unos minutos **por ruta**, y no hay
    parametro que lo salte (probado: `?v=...` devuelve X-Cache HIT igual). Como
    nuestro jar se llama siempre igual, cada publicacion abria una ventana en la
    que el manifiesto ya anunciaba una huella nueva y el CDN seguia sirviendo el
    binario viejo. A quien le diera a Jugar en ese rato, la descarga no le
    cuadraba.

    Metiendo la huella en el nombre, **contenido nuevo = URL nueva**, que nunca
    ha estado en cache. El jugador sigue teniendo el fichero con su nombre de
    siempre: `path` y `url` son campos distintos.
    """
    return f"{jar.stem}-{sha1[:10]}{jar.suffix}"


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
        sha1 = hashlib.sha1(datos).hexdigest()
        # Se copia junto al manifiesto para que `publicar()` lo suba con el. Los
        # dos tienen que viajar juntos: un manifiesto que apunta a un jar que
        # todavia no esta publicado es un launcher que falla en casa de todos.
        (SALIDA / publicado(jar, sha1)).write_bytes(datos)
        ficheros.append({
            # En el cliente conserva su nombre de siempre; lo que cambia en cada
            # compilacion es la URL. Ver `publicado()`.
            "path": f"mods/{jar.name}",
            "sha1": sha1,
            "size": len(datos),
            "url": f"{BASE_ACTIVOS}/{publicado(jar, sha1)}",
        })
        print(f"  {entrada['prefijo']:<15} {publicado(jar, sha1):<26} nuestro")

    # 5. Los OVERRIDES del pack base: su configuracion y sus resource packs.
    #
    # ⚠⚠ VIAJAN COMO UN ZIP POR CARPETA, Y NO SUELTOS. Sueltos eran 176
    #    peticiones a raw.githubusercontent, que NO es un CDN de distribucion y
    #    contesta 429 — el mismo fallo que dejo a los jugadores fuera el
    #    2026-08-16 y que costo media manana encontrar. Con el pack oficial de
    #    Cobblemon eran 3 ficheros sueltos y no se notaba; CobbleVerse trae 155
    #    de configuracion, y ahi el limite salta seguro.
    #
    #    Cada zip va marcado `keepExisting`: se extrae SIN PISAR lo que ya
    #    exista. Eso da exactamente la semantica que tenia `once` fichero a
    #    fichero --si el jugador ajusta algo, no se lo revertimos-- y ademas
    #    arregla lo que `once` hacia mal: un fichero de configuracion NUEVO en
    #    una version posterior del pack SI llega, porque no existe todavia.
    dir_cfg = SALIDA / "overrides"
    if dir_cfg.exists():
        shutil.rmtree(dir_cfg)
    carpetas = {}
    for n in overrides:
        rel = n[len("overrides/"):]
        datos = gen_modpack.contenido(z, n)
        destino = dir_cfg / rel
        destino.parent.mkdir(parents=True, exist_ok=True)
        destino.write_bytes(datos)
        raiz = rel.split("/")[0]
        if "/" not in rel:          # servers.dat y demas sueltos de la raiz
            continue                 # los generamos nosotros; ver el paso 6
        carpetas.setdefault(raiz, []).append((rel, datos))

    # `mods/` NUNCA se sirve como carpeta extraible, aunque venga en los
    # overrides. Una entrada `archive` de nombre `mods` se borraria ENTERA el
    # dia que ese override desaparezca --`applySync` limpia lo que ya no esta
    # en el manifiesto con `rm -r`-- y ahi dentro viven los 400 MB de mods.
    # Los jars sueltos van uno a uno, como los nuestros.
    for rel, datos in carpetas.pop("mods", []):
        sha = hashlib.sha1(datos).hexdigest()
        nombre = f"{Path(rel).stem}-{sha[:10]}.jar"
        (SALIDA / nombre).write_bytes(datos)
        ficheros.append({"path": rel, "sha1": sha, "size": len(datos),
                         "url": f"{BASE_ACTIVOS}/{nombre}"})
        print(f"  override        {Path(rel).name:<40} suelto")

    resumen = []
    for raiz, entradas in sorted(carpetas.items()):
        datos_zip = zip_carpeta(entradas, raiz + "/")
        sha_zip = hashlib.sha1(datos_zip).hexdigest()
        (SALIDA / f"{raiz}.zip").write_bytes(datos_zip)
        ficheros.append({
            "path": raiz,
            "sha1": sha_zip,
            "size": len(datos_zip),
            "url": f"{BASE_ACTIVOS}/{raiz}-{sha_zip[:10]}.zip",
            "archive": True,
            "keepExisting": True,
            # ⚠ Y TAMBIEN `once`, que parece redundante y NO LO ES.
            #
            # `keepExisting` solo lo entiende el launcher nuevo, y los que ya
            # estan repartidos ignoran el campo y extraen PISANDO. `once` si lo
            # entienden todos --y se mira ANTES que `archive`--, asi que a un
            # launcher viejo esto le dice "extrae solo si no esta la carpeta".
            # Sin esta linea, actualizar el pack le borraba a la gente su
            # configuracion; a el le petaba con EPERM en un fichero oculto.
            #
            # Se paga con que un fichero de config NUEVO no llega a quien ya
            # tenga la carpeta. Se cobra cuando todos hayan actualizado.
            "once": True,
        })
        resumen.append(f"{raiz} {len(entradas)} ficheros "
                       f"({len(datos_zip) // 1024} KB)")
    print("  overrides       " + " · ".join(resumen))

    # 6. Ficheros nuestros que se escriben una vez y no se vuelven a tocar.
    #
    #    La lista de servidores va como `once`: si se reescribiera en cada
    #    arranque, borrariamos los servidores que el jugador haya anadido por su
    #    cuenta. Y los shaders llegan APAGADOS: en cuanto active uno, su
    #    eleccion sobrevive a todas las actualizaciones siguientes.
    #
    #    ⚠ VAN A LA RELEASE, NO A `raw`. Eran los dos ultimos ficheros del pack
    #    que se pedian a `raw.githubusercontent`, que limita por peticiones y
    #    contesta 429. Con la huella en el nombre valen las mismas dos reglas
    #    que ya rigen para nuestros jars: contenido nuevo = URL nueva, que nunca
    #    ha estado en cache, y el fichero viejo sigue existiendo para quien
    #    tenga un manifiesto anterior. `path` y `url` son campos distintos, asi
    #    que el jugador sigue recibiendolo con su nombre de siempre.
    for nombre, ruta, datos in (
            ("servers.dat", "servers.dat", servers_dat(*SERVIDOR)),
            ("iris.properties", "config/iris.properties",
             IRIS_PROPERTIES.encode("utf-8"))):
        (SALIDA / nombre).write_bytes(datos)
        sha = hashlib.sha1(datos).hexdigest()
        tallo, punto, ext = nombre.partition(".")
        (SALIDA / f"{tallo}-{sha[:10]}{punto}{ext}").write_bytes(datos)
        ficheros.append({
            "path": ruta,
            "sha1": sha,
            "size": len(datos),
            "url": f"{BASE_ACTIVOS}/{tallo}-{sha[:10]}{punto}{ext}",
            # `raw` se queda de ESPEJO, no de origen: si la release fallara, el
            # launcher tiene a donde ir. Ver `con_espejos()`.
            "espejo": f"{BASE_RAW}/{nombre}",
            "once": True,
        })

    # 6-bis. REGLAS DEL SERVIDOR, y estas SI se pisan.
    #
    # ⚠⚠⚠ AL CONTRARIO QUE LAS DE ARRIBA, NO VAN `once`. Lo de arriba es
    #    configuracion DEL JUGADOR --sus servidores, su shader-- y pisarla seria
    #    borrarle lo que eligio. Esto son REGLAS DEL SERVIDOR: si un cliente se
    #    quedara con su copia vieja, su carta anunciaria un bono que el servidor
    #    no aplica. Un numero que miente es peor que no ensenarlo.
    #
    # ⚠ El fichero vive en `config/` del repositorio y no aqui dentro, para que
    #   sea EL MISMO que se sube al servidor. Dos copias del mismo ajuste es
    #   como acaban diciendo cosas distintas.
    for ruta in ("config/cobblemon-cards.json",):
        origen = RAIZ / ruta
        if not origen.exists():
            raise SystemExit(f"Falta {ruta}: es una regla del servidor, no un "
                             f"adorno. Ver config/README.md")
        datos = origen.read_bytes()
        sha = hashlib.sha1(datos).hexdigest()
        nombre = origen.name
        tallo, punto, ext = nombre.partition(".")
        (SALIDA / f"{tallo}-{sha[:10]}{punto}{ext}").write_bytes(datos)
        ficheros.append({
            "path": ruta,
            "sha1": sha,
            "size": len(datos),
            "url": f"{BASE_ACTIVOS}/{tallo}-{sha[:10]}{punto}{ext}",
        })
    print("  reglas          config/cobblemon-cards.json (se pisa siempre)")

    # Se comprueban los DOS perfiles por separado. El de jugador no es un
    # subconjunto inofensivo: es el que corre casi todo el mundo, y un fallo
    # ahi no lo ve nadie hasta que le da a Jugar y le sale
    # "Incompatible mods found!". Pasó de verdad con Shine y fabric-api.
    for perfil in ("jugador", "constructor"):
        print(f"  perfil {perfil}:", end=" ")
        verificar_dependencias([f for f in ficheros
                                if "constructor" not in f.get("profiles", [])
                                or perfil == "constructor"])

    host, puerto = SERVIDOR[1].split(":")
    # Se montan los espejos AQUI y no en cada sitio que crea una entrada: son
    # ocho sitios distintos y la regla es una sola. Ponerla en cada uno es la
    # forma segura de que el noveno se olvide.
    ficheros = [con_espejos(f) for f in ficheros]

    # ⚠ NI UNA RUTA REPETIDA, Y NO ES POR PULCRITUD.
    #
    # `applySync` descarga DIEZ ficheros a la vez. Dos entradas con la MISMA
    # ruta son dos escrituras simultaneas sobre el mismo fichero, y de ahi sale
    # un jar a medias — el `ZipFile invalid LOC header` que ya costo una tarde,
    # con la gracia de que el fallo aparece mucho despues y en un sitio que no
    # se parece en nada a la causa.
    #
    # Aparecio al pasar la base a CobbleVerse: ellos ya traen EuphoriaPatcher y
    # ComplementaryUnbound, que nosotros ademas anadiamos por nuestra cuenta.
    # Con el pack de Cobblemon no pasaba porque no los traia.
    #
    # Se avisa ademas de deduplicar: si algun dia se repite una ruta con
    # CONTENIDO distinto, eso no es un duplicado sino un conflicto, y quedarse
    # callado con el primero seria elegir a cara o cruz.
    vistos, unicos = {}, []
    for f in ficheros:
        anterior = vistos.get(f["path"])
        if anterior is None:
            vistos[f["path"]] = f
            unicos.append(f)
            continue
        if anterior["sha1"] != f["sha1"]:
            raise SystemExit(
                f"CONFLICTO: '{f['path']}' aparece dos veces con contenido "
                f"distinto ({anterior['sha1'][:10]} y {f['sha1'][:10]}). "
                f"Decide cual va antes de publicar.")
        print(f"  duplicado       {f['path']} (lo trae la base y lo aniadiamos "
              f"tambien nosotros)")
    ficheros = unicos

    return {
        "packVersion": VERSION_PACK,
        "minecraft": MC,
        # Se consulta, no se escribe: Cobblemon exige un minimo y un numero a
        # mano caduca en silencio. `loader_estable()` ya lo comprueba.
        "fabricLoader": loader_estable(),
        "server": {"name": "PokeReport : Luna Eternal", "host": host, "port": int(puerto)},
        "files": ficheros,
    }


def mods_publicados() -> set:
    """Los jars que sirve AHORA MISMO el manifiesto vigente, o None si no se puede."""
    import urllib.request
    try:
        puntero = json.loads(urllib.request.urlopen(
            f"{BASE_PUNTERO}/latest.json", timeout=30).read())
        vivo = json.loads(urllib.request.urlopen(
            puntero["manifest"], timeout=60).read())
    except Exception as e:                       # noqa: BLE001
        print(f"  no he podido leer el manifiesto vigente ({e}):")
        print("  NO puedo comprobar si esta publicacion quita algun mod")
        return None
    return {f["path"] for f in vivo["files"] if f["path"].startswith("mods/")}


def comprobar_bajas(manifiesto: dict, permitir: bool) -> None:
    """
    Se niega a publicar si esta version QUITA un mod que hoy esta servido.

    ⚠⚠⚠ ESTA COMPROBACION EXISTE POR UN FALLO REAL Y CARO. El 2026-09-03 una
       republicacion rutinaria --solo cambiaba nuestro jar-- se dejo fuera
       `cobblemon-cards`, que el servidor si tiene. Resultado: nadie podia
       entrar, con un mensaje que habla de «entradas de registro desconocidas» y
       no de un mod que falta.

    ⚠⚠ QUITAR UN MOD NO SE VE EN LA SALIDA DEL GENERADOR. El generador imprime
       lo que PONE, mod a mod; lo que se deja fuera no aparece por ningun sitio,
       porque no hay nada que imprimir. Un diff contra lo que hay publicado es
       la unica forma de ver una ausencia.

    ⚠ Quitar un mod es legitimo --a veces es justo lo que se quiere-- pero tiene
      que decirlo alguien: `--permitir-bajas`.
    """
    antes = mods_publicados()
    if antes is None:
        return
    ahora = {f["path"] for f in manifiesto["files"] if f["path"].startswith("mods/")}
    bajas = sorted(antes - ahora)
    if not bajas:
        return
    print()
    print("  ESTA PUBLICACION QUITARIA %d mod(s) DEL CLIENTE:" % len(bajas))
    for b in bajas:
        print("    - %s" % b.rsplit("/", 1)[-1])
    if not permitir:
        raise SystemExit(
            "\n  Me niego a publicar. Si el servidor todavia tiene alguno de "
            "esos mods,\n  NADIE PODRA ENTRAR: el servidor manda sus entradas "
            "de registro y el\n  cliente que no las conoce se queda en la "
            "puerta.\n\n  Comprueba el servidor:  python tools/mods_servidor.py"
            "\n  Si la baja es a proposito:  --permitir-bajas")
    print("  ...y se publica igual porque lo has pedido (--permitir-bajas)")


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

    # ⚠ SIN ESTO GIT CAMBIA LOS FINALES DE LINEA Y EL MANIFIESTO MIENTE.
    #
    # El repositorio del pack es un ALMACEN DE FICHEROS, no codigo fuente: lo
    # que se sube tiene que llegar al jugador byte a byte. Sin `-text`, git
    # convierte CRLF a LF al hacer commit en cualquier fichero que le parezca
    # texto, y entonces:
    #
    #   el manifiesto anuncia el tamano y el sha1 del fichero LOCAL (con CRLF)
    #   el CDN sirve el fichero NORMALIZADO (con LF), mas corto
    #
    # Medido: `config/yosbr/options.txt` son 8.150 B aqui y 7.921 alli --229 de
    # diferencia, que son sus 229 lineas-- y el sha1 no coincide. Eran 24
    # ficheros de configuracion, y el launcher los daba por corruptos y volvia a
    # bajarlos EN CADA ARRANQUE, para siempre.
    (clon / ".gitattributes").write_text(
        "# El pack es un almacen de ficheros: nada de tocar finales de linea.\n"
        "* -text\n", encoding="utf-8")

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
    # El revestido de la Pokedex se sirvio un rato como .zip suelto aqui. Ahora
    # va incrustado en el jar de `lunaneon`, asi que esta carpeta sobra: si se
    # dejara, `raw` seguiria sirviendo un pack que ya nadie instala.
    if (clon / "resourcepacks").exists():
        shutil.rmtree(clon / "resourcepacks")

    mods = clon / "mods"
    mods.mkdir(exist_ok=True)
    vigentes = set()
    for entrada in PROPIOS:
        jar = jar_propio(entrada["carpeta"], entrada["prefijo"])
        datos = jar.read_bytes()
        nombre = publicado(jar, hashlib.sha1(datos).hexdigest())
        (mods / nombre).write_bytes(datos)
        vigentes.add(nombre)
    # ⚠ EL JAR ANTERIOR NO SE BORRA. SE JUBILA UNA PUBLICACION DESPUES.
    #
    # Borrarlo a la vez que se publica el nuevo daba HTTP 404 en el launcher, y
    # el motivo tardo en verse: `raw.githubusercontent` cachea el manifiesto
    # unos tres minutos POR RUTA. Durante esa ventana el cliente sigue leyendo
    # el manifiesto VIEJO --que apunta al jar viejo-- y ese jar ya no existe.
    #
    # Se noto al publicar dos veces seguidas en pocos minutos, que es justo lo
    # que pasa cuando se esta iterando con alguien delante.
    #
    # Guardando una generacion, el manifiesto cacheado siempre encuentra su
    # fichero. Cuestan unos megas en un repositorio que ya pesa cientos, y a
    # cambio el jugador nunca se queda tirado a mitad de actualizacion.
    conservar = set(vigentes)
    for entrada in PROPIOS:
        anteriores = sorted(
            (p for p in mods.glob(f"{entrada['prefijo']}-*.jar")
             if p.name not in vigentes),
            key=lambda p: p.stat().st_mtime, reverse=True)
        for p in anteriores[:1]:
            conservar.add(p.name)
            print(f"  se conserva {p.name} (por el cache de ~3 min)")

    for viejo in mods.glob("*.jar"):
        if viejo.name not in conservar:
            viejo.unlink()
            print(f"  retirado {viejo.name}")

    # Se vuelve a registrar TODO bajo las reglas de `.gitattributes`.
    #
    # Poner el fichero no basta: los blobs que git ya tenia guardados siguen
    # normalizados, y git no los reescribe porque, para el, el contenido no ha
    # cambiado. `rm --cached` los saca del indice sin tocar el disco y el `add`
    # siguiente los vuelve a leer, ahora sin convertir nada.
    #
    # Es idempotente y sale barato: una vez arreglado, el `add` no encuentra
    # diferencias y el commit sale vacio.
    # Los activos van a la release, no al repositorio: ver TAG_ACTIVOS.
    import json as _json
    manifiesto = _json.loads((SALIDA / "manifest.json").read_text(encoding="utf-8"))
    activos = []
    for f in manifiesto["files"]:
        if not f.get("archive"):
            continue
        # El zip se genero como `<carpeta>.zip`; en la release lleva la huella
        # en el nombre, que es lo que garantiza que nunca se sirva de cache un
        # contenido viejo con la URL nueva.
        nombre = f["url"].rsplit("/", 1)[-1]
        (SALIDA / nombre).write_bytes((SALIDA / f"{f['path']}.zip").read_bytes())
        activos.append(SALIDA / nombre)
    for f in manifiesto["files"]:
        # Los jars sueltos que salen de los overrides ya estan escritos en
        # build/ con su nombre con huella; solo hay que subirlos.
        if f["url"].startswith(BASE_ACTIVOS) and not f.get("archive"):
            suelto = SALIDA / f["url"].rsplit("/", 1)[-1]
            if suelto.exists() and suelto not in activos:
                activos.append(suelto)
    for entrada in PROPIOS:
        jar = jar_propio(entrada["carpeta"], entrada["prefijo"])
        datos = jar.read_bytes()
        nombre = publicado(jar, hashlib.sha1(datos).hexdigest())
        (SALIDA / nombre).write_bytes(datos)
        activos.append(SALIDA / nombre)
    # Sin duplicados: nuestros jars los alcanzan los DOS bucles --el de los
    # sueltos de overrides y el de PROPIOS-- y `gh release upload` falla en seco
    # si le llega dos veces la misma ruta.
    subir_activos(list(dict.fromkeys(activos)))

    # EL PUNTERO SE MUEVE AQUI: con todos los activos ya arriba y antes del
    # push al repositorio, que a partir de ahora es solo la copia de respaldo
    # para los launchers 1.0.x. Ver `publicar_puntero()`.
    corta = publicar_puntero(manifiesto)

    subprocess.run(["git", "-C", str(clon), "rm", "--cached", "-r", "-q", "."],
                   check=True)
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
    # El respaldo en `raw` sigue teniendo su ventana de cache de 3 minutos, pero
    # ya NO es el camino normal: el launcher nuevo lee el puntero, que sale del
    # CDN de descargas y no cachea por ruta. Se sigue esperando porque mientras
    # queden launchers 1.0.x ese fichero es el suyo.
    esperar_al_cdn(SALIDA / "manifest.json")
    print(f"\n  vigente     {URL_PUNTERO}")
    print(f"  manifiesto  manifest-{corta}.json")
    print(f"  respaldo    {BASE_RAW}/manifest.json  (launchers 1.0.x)")
    print(f"\n  Para volver a esta version:  "
          f"python tools/gen_manifest.py --volver-a {corta}")


def esperar_al_cdn(local: Path, limite: int = 300) -> None:
    """No dice «publicado» hasta que el CDN sirve de verdad lo que se ha subido.

    POR QUE ESTO EXISTE

    `raw.githubusercontent` cachea unos tres minutos POR RUTA. Empujar al
    repositorio no es publicar: durante esa ventana el mundo sigue viendo el
    manifiesto anterior. Ya ha mordido dos veces, y la segunda de la forma mas
    tonta posible — el launcher del usuario se ejecuto 2 min 51 s despues de
    empujar, leyo el manifiesto cacheado, dio por bueno el jar que ya tenia y le
    dejo el juego sin los iconos nuevos. Todo correcto en el repositorio, y en su
    pantalla no habia cambiado nada.

    Decir «publicado» cuando aun no lo esta es peor que tardar tres minutos: hace
    que la siguiente comprobacion sea "y entonces por que no lo veo".
    """
    # ⚠ SE COMPARA EL JSON, NO LOS BYTES, y no es un detalle.
    #
    # El primer intento comparaba sha1 de bytes y no acertaba NUNCA: git
    # normaliza los finales de linea al hacer commit, asi que el fichero local
    # (CRLF, 60.169 B en Windows) y el que sirve el CDN (LF, 58.833 B) no
    # coinciden byte a byte aunque digan exactamente lo mismo. La primera
    # ejecucion se paso los cinco minutos de espera y aviso de un problema que
    # no existia: el manifiesto llevaba rato publicado.
    esperado = json.loads(local.read_text(encoding="utf-8"))
    url = f"{BASE_RAW}/manifest.json"
    print("  esperando a que el CDN lo sirva", end="", flush=True)
    arranque = time.time()
    while time.time() - arranque < limite:
        try:
            # `Cache-Control: no-cache` NO salta el cache de raw: el CDN sirve lo
            # que tiene. Se comprueba a las bravas, releyendo cada pocos
            # segundos hasta que el contenido coincide.
            datos = urllib.request.urlopen(
                urllib.request.Request(url, headers={"User-Agent": "luna-eternal"})
            ).read()
            servido = json.loads(datos.decode("utf-8"))
        except (OSError, ValueError):
            servido = None
        if servido == esperado:
            print(f"  listo ({int(time.time() - arranque)} s)")
            return
        print(".", end="", flush=True)
        time.sleep(10)
    print(f"\n  AVISO: {int(limite)} s y el CDN sigue sirviendo el manifiesto "
          f"anterior.\n  Lo subido es correcto; solo hay que esperar antes de "
          f"abrir el launcher.")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--publicar", action="store_true",
                    help="ademas de generarlo, subirlo al repositorio publico")
    ap.add_argument("--volver-a", metavar="HUELLA", dest="volver_a",
                    help="devolver el pack a un manifiesto anterior (10 caracteres). "
                         "No regenera nada: solo reescribe el puntero")
    ap.add_argument("--permitir-bajas", action="store_true",
                    help="publicar aunque esta version QUITE mods del cliente. "
                         "Sin esto se aborta: si el servidor todavia los tiene, "
                         "nadie puede entrar")
    args = ap.parse_args()

    # Volver atras no genera nada: si el pack esta roto, lo ultimo que se quiere
    # es reconstruirlo con las mismas fuentes que lo rompieron.
    if args.volver_a:
        volver_a(args.volver_a)
        return

    print(f"MANIFIESTO DEL PACK  ·  Minecraft {MC}")
    manifiesto = construir()

    destino = SALIDA / "manifest.json"
    destino.write_text(json.dumps(manifiesto, indent=2, ensure_ascii=False),
                       encoding="utf-8")
    total = sum(f.get("size", 0) for f in manifiesto["files"]) // 1048576
    print(f"\n  -> {destino}  ({len(manifiesto['files'])} ficheros, {total} MB)")
    print(f"     Fabric Loader {manifiesto['fabricLoader']}")

    if args.publicar:
        # ⚠ ANTES de subir nada: una publicacion que QUITA un mod del cliente
        #   deja fuera a todo el mundo si el servidor todavia lo tiene.
        comprobar_bajas(manifiesto, args.permitir_bajas)
        publicar()
    else:
        print("\n  Para publicarlo:  python tools/gen_manifest.py --publicar")


if __name__ == "__main__":
    main()
