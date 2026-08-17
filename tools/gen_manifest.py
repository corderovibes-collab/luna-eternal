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


def zip_plantillas(entradas: list) -> bytes:
    """Empaqueta las plantillas de YOSBR en un zip, con rutas relativas a ellas.

    DETERMINISTA A PROPOSITO: fecha fija, orden fijo y sin compresion variable.
    Si el zip cambiara de huella en cada ejecucion, el launcher se lo bajaria
    entero en cada actualizacion del pack aunque no hubiera cambiado ni un byte.
    """
    import io
    import zipfile
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for rel, datos in sorted(entradas):
            dentro = rel[len("config/yosbr/"):]
            info = zipfile.ZipInfo(dentro, date_time=(1980, 1, 1, 0, 0, 0))
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
# `lunaeternal` TAMBIEN va al cliente desde que existe el PokePad (D-025): la
# interfaz vive en su conjunto de fuentes `client`, y sin el jar no hay
# pantalla que abrir.
#
# Su jar lleva dentro la logica de economia y el conector de MariaDB, que un
# cliente no usa. Es el precio aceptado de D-025 y no es un descuido:
# separarlo en dos mods obligaria a duplicar los tipos que viajan entre
# cliente y servidor, y eso se desincroniza a la primera. Credenciales NO
# lleva: se leen de `config/` en el servidor y nunca entran en el jar.
PROPIOS = [
    {"carpeta": "neon", "prefijo": "lunaneon"},
    {"carpeta": "mod", "prefijo": "lunaeternal"},
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

    # 5. La configuracion del pack oficial. Son 113 ficheros de texto, 143 KB
    #    en total, que afinan los mods. Van marcados `once`: se escriben si
    #    faltan y no se pisan nunca, para que si un jugador ajusta algo no se lo
    #    revertamos en la siguiente actualizacion.
    dir_cfg = SALIDA / "overrides"
    if dir_cfg.exists():
        shutil.rmtree(dir_cfg)
    plantillas, sueltos = [], []
    for n in overrides:
        rel = n[len("overrides/"):]
        datos = z.read(n)
        destino = dir_cfg / rel
        destino.parent.mkdir(parents=True, exist_ok=True)
        destino.write_bytes(datos)
        # Las plantillas de YOSBR van en un zip; el resto, sueltas.
        (plantillas if rel.startswith("config/yosbr/") else sueltos).append(
            (rel, datos))

    # LAS 110 PLANTILLAS DE YOSBR VIAJAN COMO UN SOLO ZIP.
    #
    # Se puede porque NO son la configuracion del jugador: son las plantillas de
    # las que YOSBR copia cuando el fichero de verdad no existe. Sobrescribirlas
    # no le toca a nadie sus ajustes, asi que no necesitan `once` y se pueden
    # extraer encima.
    #
    # `config/iris.properties` y los dos de `openloader` se quedan SUELTOS y con
    # `once`: esos si son configuracion viva.
    zip_datos = zip_plantillas(plantillas)
    sha_zip = hashlib.sha1(zip_datos).hexdigest()
    (SALIDA / "yosbr.zip").write_bytes(zip_datos)
    ficheros.append({
        "path": "config/yosbr",
        "sha1": sha_zip,
        "size": len(zip_datos),
        "url": f"{BASE_ACTIVOS}/yosbr-{sha_zip[:10]}.zip",
        "archive": True,
    })
    for rel, datos in sueltos:
        ficheros.append({
            "path": rel,
            "sha1": hashlib.sha1(datos).hexdigest(),
            "size": len(datos),
            "url": f"{BASE_RAW}/overrides/{urllib.parse.quote(rel)}",
            "once": True,
        })
    print(f"  configuracion   {len(plantillas)} plantillas en 1 zip "
          f"({len(zip_datos) // 1024} KB) + {len(sueltos)} sueltas")

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
    activos = [SALIDA / "yosbr.zip"]
    import json as _json
    manifiesto = _json.loads((SALIDA / "manifest.json").read_text(encoding="utf-8"))
    nombre_zip = next(f["url"].rsplit("/", 1)[-1] for f in manifiesto["files"]
                      if f.get("archive"))
    (SALIDA / nombre_zip).write_bytes((SALIDA / "yosbr.zip").read_bytes())
    activos = [SALIDA / nombre_zip]
    for entrada in PROPIOS:
        jar = jar_propio(entrada["carpeta"], entrada["prefijo"])
        datos = jar.read_bytes()
        nombre = publicado(jar, hashlib.sha1(datos).hexdigest())
        (SALIDA / nombre).write_bytes(datos)
        activos.append(SALIDA / nombre)
    subir_activos(activos)

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
    esperar_al_cdn(SALIDA / "manifest.json")
    print(f"  publicado en {BASE_RAW}/manifest.json")


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
