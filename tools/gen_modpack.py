#!/usr/bin/env python3
"""
Genera el pack de cliente (.mrpack) partiendo del MODPACK OFICIAL de Cobblemon.

DE DONDE SALE LA LISTA DE MODS

De ellos, no de aqui. Se descarga el `.mrpack` oficial, se lee su indice y se
usan **sus versiones exactas**: las que ellos han probado juntas. Encima se
aplican dos capas nuestras:

    base        76 ficheros del pack oficial (Cobblemon 1.7.3, MC 1.21.1)
    - EXCLUIDOS lo que quitamos, con el motivo escrito al lado
    + EXTRA     lo que anadimos (Axiom, WorldEdit, nuestros mods, shaders)

Asi, cuando Cobblemon saque pack nuevo, actualizar es volver a ejecutar esto.
No hay una lista de 76 lineas que mantener a mano — que es justo el tipo de
lista que se queda obsoleta sin que nadie se entere.

QUE NO SE COPIA DE SU PACK, Y POR QUE

Su carpeta de configuracion pesa 106 MB, y **97 son un mundo tutorial de un
jugador**. Nuestros jugadores entran a un servidor: ese mundo es peso muerto.
El menu de inicio de FancyMenu tampoco se copia — lleva la marca de Cobblemon,
y este pack se llama PokeReport. Lo que si se copia son los 108 ficheros de
configuracion de verdad (143 KB) que afinan los mods.

LICENCIAS (D-008)

El .mrpack NO redistribuye ningun mod ajeno: guarda URLs y hashes, y el
launcher los baja de Modrinth. Por eso las licencias restrictivas de Sodium,
EntityCulling o Xaero's no son un problema — se descargan de su canal oficial.
Lo unico incrustado son NUESTROS jars.
"""
import io, json, os, re, struct, urllib.error, urllib.parse, urllib.request, zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SALIDA = RAIZ / "build"
UA = {"User-Agent": "PokeReport-LunaEternal/0.1 (dev)"}

MC = "1.21.1"
SERVIDOR = ("§6PokeReport §f: §bLuna Eternal", "s12.mia.us.tarohosting.lat:33043")

# Version minima del cargador. Cobblemon 1.7.3 la exige y no arranca sin ella:
# "requires version 0.17.2 or later of mod 'Fabric Loader'". Estaba escrita a
# mano en 0.16.14 y el pack generado no arrancaba (2026-08-11).
LOADER_MINIMO = (0, 17, 2)

# ---------------------------------------------------------------------------
# LA BASE (D-031, sustituye a D-024)
# ---------------------------------------------------------------------------
# ⚠ 2026-08-17: LA BASE PASA A SER COBBLEVERSE. Decision del usuario, tomada
# despues de que le enseñara las licencias:
#
#   cobbleverse           modpack   All Rights Reserved
#   cobbleverse-badges    mod       CC-BY-NC-ND-4.0
#
# Queda dicho aqui porque D-006 lo habia descartado justo por eso, y quien lea
# esto dentro de seis meses tiene que ver las dos cosas: el dato y la decision.
#
# Lo que SI se hace bien: el manifiesto guarda URL y hash, nunca el jar, asi que
# cada mod se descarga del CDN de Modrinth igual que hoy. No redistribuimos
# nada. Y de la base se quitan sus DATAPACKS y sus SHADERPACKS --contenido
# propio suyo-- ademas de la generacion de estructuras, que el usuario no quiere.
PACK_BASE = "cobbleverse"

# Lo que se quita de la base. La clave es el slug de Modrinth; el valor es el
# motivo, y esta escrito para que dentro de seis meses nadie lo vuelva a anadir
# "porque venia en el oficial".
EXCLUIDOS = {
    "stendhal":
        "CC-BY-NC-ND-4.0. El NC prohibe el uso con animo comercial y el plan "
        "incluye venta de paquetes (D-007). No es cuestion de redistribuir: es "
        "USARLO lo que no se puede. Es la misma clausula que descarto "
        "CobbleVerse (D-006)",
    "bisect-mod":
        "Es el mod de integracion de BisectHosting: publicidad de un hosting "
        "que no es el nuestro. Nuestro servidor esta en TaroHosting",
    # --- generacion de mundo y estructuras: NO se quiere (peticion del usuario)
    "legendary-monuments":
        "Genera monumentos por el mundo. El usuario no quiere generacion de "
        "construcciones: la ciudadela se construye a mano",
    "repurposed-structures-fabric":
        "Genera estructuras nuevas por todo el mundo. Mismo motivo. OJO con el "
        "slug: el jar se llama repurposed_structures pero en Modrinth el "
        "proyecto es -fabric, y sin el sufijo la exclusion NO SURTE EFECTO y "
        "no avisa de nada",
    "biome-replacer":
        "Reemplaza biomas enteros. Cambia la generacion del mundo, que es lo "
        "que se quiere dejar quieto",
    "huge-structure-blocks":
        "Solo existe para colocar las estructuras gigantes de Legendary "
        "Monuments. Sin ellas no pinta nada",

    # --- musica: 421 MB, mas del doble de todo el pack de hoy --------------
    # No es un juicio sobre la musica: es que multiplicaba por cinco la
    # descarga de un jugador nuevo (P10). Se pueden volver a meter el dia que
    # el manifiesto sepa ofrecer extras opcionales.
    "puffradio":
        "124 MB de emisora de radio. Ver el bloque de musica",
    "cobblemon-original-pokemon-battle-music":
        "93 MB de musica de combate. Ver el bloque de musica",
    "pokediscs":
        "25 MB de discos. Ver el bloque de musica",

    # ⚠⚠ ESTOS DOS ECHABAN A LA GENTE DEL SERVIDOR. Reportado en vivo el
    #    2026-08-17: "Failed to decode packet 'clientbound/custom_payload'",
    #    y en el log del cliente, la causa de verdad:
    #        StructFieldException: [Field: exported_slots]  (owo-lib / endec)
    #
    # `exported_slots` es de Accessories, que viene con mega_showdown. El
    # puente Trinkets<->Accessories REGISTRA RANURAS, y las ranuras se
    # sincronizan al entrar: con el puente en el cliente y no en el servidor,
    # las dos listas no cuadran y la conexion se cae en la puerta.
    #
    # Se quitan del CLIENTE en vez de anadirse al servidor porque se comprobo
    # quien los usa: `accessories-compat-layer` depende de `trinkets`, y de
    # `accessories-compat-layer` NO DEPENDE NADIE en los 147 mods. Es un puente
    # que no cruza nadie — esta ahi por si alguien mete un mod de Trinkets.
    # Quitarlos iguala los dos lados sin gastar RAM del servidor.
    #
    # ⚠ Si algun dia entra un mod que use Trinkets, hay que volver a meter los
    #   dos Y ademas ponerlos en el servidor. Nunca solo en un lado.
    "trinkets":
        "Registra ranuras de Accessories y solo estaba en el cliente: la "
        "conexion se caia con 'exported_slots'. No lo usa ningun mod del pack",
    "accessories-compat-layer":
        "El puente Trinkets<->Accessories. Mismo motivo, y de el no depende "
        "nadie",

    # ⚠ SE QUITA EL MOD, Y ANTES SE INTENTO SOLO APAGAR SU PACK.
    #
    # Continuity son TEXTURAS CONECTADAS: 42 bloques medidos, y son los de
    # construir (los 16 cristales de color, sus 16 paneles, cristal, tintado,
    # libreria y la familia de la arenisca). Una fachada de cristal deja de
    # tener rejilla y pasa a ser una lamina: el usuario lo describio como "no
    # estan los bloques que habia colocado".
    #
    # Primero se quito `continuity:default` de la lista de packs que repartimos.
    # NO BASTO, y por un motivo que conviene recordar: esa lista es una
    # PLANTILLA, y solo la copia `defaultoptions` cuando el jugador todavia no
    # tiene `options.txt`. A quien ya jugo no le llega nunca — su fichero es
    # suyo y no se pisa. Asi que el pack se veia arreglado desde aqui y el
    # jugador seguia viendolo mal.
    #
    # Quitar el JAR si le llega a todo el mundo: el launcher borra los mods que
    # ya no estan en el manifiesto, y una linea de `options.txt` que nombra un
    # pack inexistente Minecraft la ignora sin quejarse. De Continuity no
    # depende ningun mod de los 145, asi que no arrastra nada.
    "continuity":
        "Texturas conectadas en 42 bloques de construir. Cambiaba fachadas ya "
        "construidas. Apagar su pack no bastaba: la lista de packs es una "
        "plantilla y no alcanza a quien ya tiene options.txt",
}

# Los packs que CobbleVerse manda DESACTIVADOS. Se llaman, literalmente,
# "z DO NOT ENABLE z [... - Credits Only]": son la copia original de cada pack
# de modelos, incluida solo para dar credito a su autor. La version que el
# juego ENCIENDE es la suya fusionada, que viene en los overrides — se
# comprueba en `resourcePacks:[...]` de su options.txt, donde ninguno de estos
# aparece. Son 44 MB que nadie llega a cargar nunca.
MARCA_DESACTIVADO = "z DO NOT ENABLE z"

# Versiones donde la del pack oficial NO nos sirve, y por que. Se sustituyen
# por la ultima estable de Modrinth.
#
# Ojo: subir algo aqui es apartarse de lo que ellos han probado. Solo para
# librerias aditivas y con el motivo escrito.
SUBIR = {
    "fabric-api":
        "Shine 1.0.0 exige >= 0.116.9 y el pack oficial fija 0.116.8, asi que "
        "el juego ni arrancaba: 'Incompatible mods found'. Fabric API es "
        "aditiva y compatible hacia atras dentro de una misma version de "
        "Minecraft, asi que subirla es seguro. La alternativa era quitar Shine "
        "y quedarnos sin luz de color en los neones. "
        "⚠ 2026-08-17: SHINE SE QUITO, asi que este motivo YA NO EXISTE. Se "
        "mantiene la subida porque Fabric API es aditiva y quitarla en la misma "
        "publicacion que arregla un crasheo es cambiar dos cosas a la vez. "
        "Se puede volver a la 0.116.8 del pack oficial cuando haya calma",
}

# Overrides de la base que NO se copian.
OVERRIDES_FUERA = (
    # 97 MB y 2267 ficheros de un mundo tutorial de UN JUGADOR. Nuestros
    # jugadores entran directos al servidor.
    "config/yosbr/saves/",
    # El menu de inicio con la marca, la musica y las diapositivas de
    # Cobblemon. Este pack se llama PokeReport.
    "config/fancymenu/",
    "instance.png", "icon.png",
    # --- lo propio de CobbleVerse -------------------------------------------
    # 1.234 ficheros: son Complementary + Euphoria RENOMBRADOS, y su licencia
    # prohibe justo eso (D-030). Nosotros ya los repartimos por su canal
    # oficial, con su nombre real y como parcheador.
    "shaderpacks/",
    # Su contenido propio: entrenadores, progresion y ajustes de su aventura.
    "datapacks/",
    # Sus ficheros de licencia y su PDF: son suyos, no nuestros.
    "licenses/", "COBBLEVERSE - Third-Party Licenses.pdf",
    # ⚠ ESTADO LOCAL DEL PARCHEADOR, NO CONFIGURACION.
    #
    # `config/euphoria_patcher/.data.json` anota que shader parcheo Euphoria EN
    # ESA MAQUINA. Repartirlo es contarle a un PC lo que hizo otro, y ademas
    # rompio el launcher de verdad: el fichero va con atributo OCULTO en
    # Windows, y Node lanza EPERM al escribir encima de uno oculto. El usuario
    # se lo comio al pulsar JUGAR el 2026-08-17.
    "config/euphoria_patcher/",
    # 179 MB de banda sonora propia, y ademas dentro de nuestro repositorio:
    # es justo lo que D-030 no hace con los shaders. Ver el bloque de musica.
    "resourcepacks/COBBLEVERSE Soundtrack.zip",
)

# ---------------------------------------------------------------------------
# LO NUESTRO, ENCIMA DE LA BASE
# ---------------------------------------------------------------------------
# `iris`, `yacl`, `sodium`, `lithium`, `ferrite-core`, `entityculling` y
# `modmenu` ya vienen en la base: no se repiten aqui.
EXTRA_JUGADOR = [
    # Genera Euphoria Patches en el PC del jugador a partir de Complementary.
    # Es la unica via que permite su licencia (§2.1, §2.2.a) — ver
    # docs/technical/client-pack.md §2-quater.
    "euphoria-patches",
    # ⚠ SHINE SE RETIRO EL 2026-08-17. NO VOLVER A METERLO SIN LEER ESTO.
    #
    # CRASHEABA EL CLIENTE AL RENDERIZAR:
    #   IllegalStateException: Shine could not locate the expected
    #   Sodium 0.6.13 opaque vertex shader patch points
    #
    # Lleva los puntos de parcheo de Sodium 0.6.13 escritos a fuego y el pack
    # oficial sirve 0.8.12. No hay version que lo arregle: el proyecto
    # DESAPARECIO de Modrinth el 2026-08-16 (404 por slug y por id).
    #
    # ⚠ Y NUESTRA VERIFICACION NO PODIA CAZARLO: Shine no declara ninguna
    #   dependencia de Sodium en su fabric.mod.json. `verificar_dependencias()`
    #   lee lo declarado, y aqui no habia nada que leer. El choque vivia en el
    #   codigo, no en los metadatos.
    #
    # Se quita sin pena porque NO ESTABA HACIENDO NADA: su halo solo se aplica
    # a bloques declarados como emisores, y los 96 neones nunca se declararon.
    # Se crasheaba por una funcion que no estaba activa. La luz de color de
    # verdad la dan los shaders, que van instalados.

    # ⚠ ESTOS DOS ESTAN AQUI PARA QUE PUEDAN ESTAR EN EL SERVIDOR.
    #
    # El pack oficial de Cobblemon los traia y CobbleVerse no. Son de servidor
    # --juntan las bolas de experiencia y hacen que los mobs desaparezcan como
    # deberian-- pero el servidor tiene que ser SUBCONJUNTO del cliente o Fabric
    # echa a la gente con "Registry remapping failed", asi que van tambien aqui.
    #
    # Y quitarlos del servidor no era opcion: CobbleVerse mete entrenadores por
    # el mundo e incursiones, o sea que la presion de entidades SUBE. Justo
    # cuando mas falta hacen.
    "clumps",
    # Dependencia declarada de `lmd`, y va ANTES a proposito (mismo criterio
    # que malilib con litematica). LGPL-3.0-only: uso comercial permitido, la
    # misma familia de licencia que el conector de MariaDB que ya empaquetamos.
    #
    # Es de SERVIDOR (`client_side: unsupported`) y aun asi va al cliente, por
    # lo mismo que clumps y lmd: el servidor tiene que ser SUBCONJUNTO del
    # cliente o Fabric echa a la gente con "Registry remapping failed".
    "almanac",
    # ⚠ EL SLUG ES `lmd`, PERO EL JAR SIGUE LLAMANDOSE `letmedespawn-*.jar`.
    #   Modrinth renombro el proyecto y el antiguo devuelve 404, asi que el
    #   manifiesto dejo de generarse entero -- correctamente, porque publicar
    #   un pack con un mod menos deja a todo el mundo sin poder conectarse.
    #   Aqui va el slug (con el que se PREGUNTA a Modrinth); en
    #   `mods_servidor.py` va el nombre del fichero, que no ha cambiado. Son
    #   dos cosas distintas y por eso solo hace falta tocar una.
    "lmd",
]

EXTRA_CONSTRUCTOR = [
    # Dibuja la seleccion de WorldEdit. Sin el se construye a ciegas.
    "worldedit-cui",
    # Editor de construccion. docs/world/construccion.md §3-bis.
    "axiom",
    # Litematica mide y superpone un plano; malilib es su dependencia y va
    # antes a proposito.
    "malilib",
    "litematica",
]

# Shaders NUESTROS, ademas del Complementary Reimagined que ya trae la base.
# Van por URL de Modrinth y nunca copiados: §1.2.d de su licencia prohibe
# servirlos por "direct file upload".
SHADERS = ["complementary-unbound", "makeup-ultra-fast-shaders"]

# Iris arranca sin shaders puestos. Se manda el fichero igualmente y marcado
# `once` (se escribe si falta y no se pisa nunca), para que quede explicito y
# para que la eleccion del jugador sobreviva a las actualizaciones.
IRIS_PROPERTIES = (
    "# PokeReport : Luna Eternal\n"
    "# Los shaders vienen INSTALADOS y APAGADOS. Se activan en\n"
    "# Opciones > Graficos > Shader Packs.\n"
    "enableShaders=false\n"
    "shaderPack=\n"
    "disableUpdateMessage=true\n"
)

# Mods NUESTROS que van en el cliente: (carpeta, prefijo del jar). Solo
# `lunaneon` (D-029) — sin el, la ciudadela se ve como cubos de textura
# ausente. `lunaeternal` es de servidor y no se reparte.
PROPIOS = [("neon", "lunaneon")]


def api(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))


def bajar(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA)).read()


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


# ---------------------------------------------------------------------------
# PROYECTOS QUE YA NO ESTAN EN MODRINTH
#
# `shine` desaparecio del catalogo el 2026-08-16: 404 por slug Y por id
# (`uQgWIE6A`), o sea que no es un renombrado. Su jar, en cambio, SIGUE
# sirviendose desde el CDN, byte a byte el mismo que ya tienen instalado todos
# los jugadores.
#
# Se conserva fijado en vez de quitarlo por una razon concreta: ese mod es lo
# que da halo de color a los neones, y quitarlo cambiaria el aspecto de la
# ciudadela a todo el mundo de golpe. Pero es una dependencia SIN AGUAS ARRIBA,
# asi que:
#
#   1. la URL se comprueba en cada publicacion (ver `fijado()`), porque un
#      manifiesto que apunta a un 404 deja tirado a TODO el que actualice — ya
#      paso una vez con nuestro propio jar
#   2. no se puede saber si su licencia sigue permitiendo esto: la pagina del
#      proyecto ya no existe. Queda anotado como decision pendiente
# ---------------------------------------------------------------------------
# Vacio desde que se retiro `shine` (2026-08-17). La maquinaria SE QUEDA: es
# la unica forma de servir un mod cuyo proyecto desaparece de Modrinth, y
# `fijado()` comprueba la URL en cada publicacion porque un manifiesto que
# apunta a un 404 deja tirado a todo el que actualice.
FIJADOS = {}


def fijado(slug):
    """La copia guardada de un proyecto que ya no esta, si su URL responde."""
    v = FIJADOS.get(slug)
    if not v:
        return None
    url = v["files"][0]["url"]
    try:
        r = urllib.request.urlopen(
            urllib.request.Request(url, method="HEAD", headers=UA))
    except urllib.error.HTTPError as e:
        raise SystemExit(
            f"{slug} ya no esta en Modrinth y su jar fijado tampoco responde "
            f"({e.code}).\n{url}\nHay que quitarlo de EXTRA_JUGADOR o alojarlo "
            f"nosotros. NO se publica un manifiesto con un enlace roto: deja "
            f"tirado a todo el que actualice.")
    largo = int(r.headers.get("Content-Length", 0))
    if largo != v["files"][0]["size"]:
        raise SystemExit(f"{slug}: el jar fijado mide {largo} y se esperaban "
                         f"{v['files'][0]['size']}. Alguien lo ha cambiado.")
    # Sin simbolos raros: la consola de Windows va en cp1252 y un caracter
    # fuera de esa tabla revienta el print — y con el, la publicacion entera.
    print(f"  FIJADO {slug:<26} {v['version_number']:<22} ya no esta en "
          f"Modrinth, se sirve la copia que ya tienen los jugadores")
    return v


def version_de(slug, loader="fabric"):
    """La ultima version de un proyecto para nuestro Minecraft.

    Solo para los EXTRA: los de la base llegan con la version que el pack
    oficial fijo, que es la que ellos han probado.

    `loader=None` para los shaderpacks: no son mods de Fabric, Modrinth los
    etiqueta con `iris`/`optifine` y filtrar por fabric devuelve cero
    resultados."""
    q = (f"https://api.modrinth.com/v2/project/{slug}/version"
         f"?game_versions=%5B%22{MC}%22%5D")
    if loader:
        q += f"&loaders=%5B%22{loader}%22%5D"
    try:
        d = api(q)
    except urllib.error.HTTPError as e:
        # Un 404 aqui no es un fallo de red: es que el proyecto ya no existe.
        # Cualquier otro codigo si es un fallo y hay que verlo.
        if e.code != 404:
            raise
        return fijado(slug)
    # 'release' antes que beta: el cliente de un servidor no es sitio para
    # probar versiones inestables.
    return ([v for v in d if v.get("version_type") == "release"] or d or [None])[0]


# ---------------------------------------------------------------------------
# EL PACK OFICIAL
# ---------------------------------------------------------------------------

def _mrpack_base() -> Path:
    """Descarga el .mrpack oficial (96 MB) y lo cachea en build/."""
    destino = SALIDA / f"{PACK_BASE}-{MC}.mrpack"
    if destino.exists():
        return destino
    v = version_de(PACK_BASE, loader=None)
    if not v:
        raise SystemExit(f"El pack oficial {PACK_BASE} no tiene version para {MC}")
    SALIDA.mkdir(parents=True, exist_ok=True)
    print(f"  bajando el pack oficial {v['version_number']} "
          f"({v['files'][0]['size'] // 1048576} MB)...")
    destino.write_bytes(bajar(v["files"][0]["url"]))
    return destino


def base():
    """(ficheros, overrides, zip) del pack oficial, ya filtrados.

    `ficheros` viene con el slug de Modrinth resuelto para poder excluir por
    nombre en vez de por nombre de jar, que cambia en cada version.
    """
    z = zipfile.ZipFile(_mrpack_base())
    idx = json.loads(z.read("modrinth.index.json"))

    # El slug no esta en el indice, pero el ID del proyecto si: va dentro de la
    # URL de descarga. Una sola llamada en lote para los 76.
    por_id = {}
    for f in idx["files"]:
        m = re.search(r"/data/([^/]+)/versions", f["downloads"][0])
        if m:
            por_id.setdefault(m.group(1), []).append(f)
    proyectos = api("https://api.modrinth.com/v2/projects?ids="
                    + urllib.parse.quote(json.dumps(list(por_id))))
    for p in proyectos:
        for f in por_id[p["id"]]:
            f["slug"] = p["slug"]

    ficheros = []
    for f in idx["files"]:
        slug = f.get("slug")
        if MARCA_DESACTIVADO in f["path"]:
            continue
        motivo = EXCLUIDOS.get(slug)
        if motivo:
            print(f"  FUERA  {slug:<22} {motivo[:64]}...")
            continue
        if slug in SUBIR:
            v = version_de(slug)
            nuevo = v["files"][0]
            if nuevo["filename"] != f["path"].split("/")[-1]:
                print(f"  SUBIDO {slug:<22} {v['version_number']} "
                      f"(el pack fijaba {f['path'].split('/')[-1]})")
                f = {"path": f"mods/{nuevo['filename']}",
                     "hashes": {"sha1": nuevo["hashes"]["sha1"],
                                "sha512": nuevo["hashes"]["sha512"]},
                     "env": f["env"], "downloads": [nuevo["url"]],
                     "fileSize": nuevo["size"], "slug": slug}
        ficheros.append(f)

    overrides = [n for n in z.namelist()
                 if n.startswith("overrides/") and not n.endswith("/")
                 and not any(n[10:].startswith(x) for x in OVERRIDES_FUERA)]

    # Que resource packs SOBREVIVEN, para poder limpiar la lista de activos de
    # su options.txt (ver `contenido`). Salen de los dos sitios: los que baja
    # el launcher de Modrinth y los que vienen dentro de los overrides.
    _PACKS_VIVOS.clear()
    _PACKS_VIVOS.update(
        n.split("/")[-1] for n in
        [f["path"] for f in ficheros] + [o[len("overrides/"):] for o in overrides]
        if n.startswith("resourcepacks/"))
    return ficheros, overrides, z


# Nombres de fichero de los resource packs que quedan en el pack.
_PACKS_VIVOS: set = set()

# ---------------------------------------------------------------------------
# PACKS QUE VIENEN ACTIVADOS EN SU options.txt Y QUE NOSOTROS APAGAMOS
# ---------------------------------------------------------------------------
# ⚠ 2026-08-17. Lo reporto el usuario: "desde que instalamos cobbleverse
#   algunos bloques de mi construccion se cambiaron de textura, ahora son otra
#   cosa". No era imaginacion suya y no era el arte: es UN pack concreto.
#
# `continuity:default` son TEXTURAS CONECTADAS, y se midio a que afecta: 42
# bloques, y son justo los de construir --los 16 cristales de color, sus 16
# paneles, el cristal normal, el tintado, la libreria y la familia entera de la
# arenisca--. Una fachada de cristal deja de ser una cuadricula y pasa a ser una
# lamina continua: el bloque es el mismo, pero la fachada NO ES LA MISMA.
#
# Se apaga SOLO ese. `continuity:glass_pane_culling_fix` se queda: ese no
# repinta nada, arregla que se vea el canto de los paneles de cristal.
#
# Y se apaga el PACK, no se quita el mod: Continuity sin sus texturas es un
# motor sin nada que dibujar, no molesta a nadie, y quien lo quiera lo enciende
# en Opciones > Paquetes de recursos.
PACKS_APAGADOS = ()   # continuity se quita entero, ver EXCLUIDOS


def contenido(z, nombre: str) -> bytes:
    """Lee un override del pack base, parcheando lo que haga falta.

    Hoy solo una cosa, y no es cosmetica: su `options.txt` trae la lista de
    resource packs ACTIVOS, y ahi siguen nombrados los que hemos quitado (la
    musica, la banda sonora). Minecraft ignora los que no encuentra, asi que no
    rompe nada — pero deja al jugador una lista con fantasmas, y a quien mire
    el fichero dentro de un ano le hara creer que esos packs deberian estar.
    """
    datos = z.read(nombre)
    if not nombre.endswith("options.txt"):
        return datos
    texto = datos.decode("utf-8", "replace")

    def limpiar(m):
        vivos = [e for e in json.loads(m.group(1))
                 if e not in PACKS_APAGADOS
                 and (not e.startswith("file/") or e[5:] in _PACKS_VIVOS)]
        return "resourcePacks:" + json.dumps(vivos, ensure_ascii=False)

    return re.sub(r"resourcePacks:(\[.*?\])", limpiar, texto).encode("utf-8")


# ---------------------------------------------------------------------------
# VERIFICACION DE DEPENDENCIAS
#
# Existe por un fallo que llego hasta la pantalla del jugador: Shine exigia
# fabric-api >= 0.116.9, el pack oficial fijaba 0.116.8, y Fabric se negaba a
# arrancar con "Incompatible mods found!". Mezclar las versiones que fija un
# pack con mods resueltos a la ultima **obliga** a comprobar el resultado.
#
# Se lee el `fabric.mod.json` de cada jar, no los metadatos de Modrinth: la
# dependencia de verdad esta dentro del jar. Se cachea por SHA1, asi que solo
# la primera ejecucion paga la descarga.
# ---------------------------------------------------------------------------

CACHE_META = SALIDA / "meta-mods-v2.json"

# Lo aporta el entorno, no un jar del pack.
LO_PONE_EL_JUEGO = {"minecraft", "java", "fabricloader", "fabric"}


def _sha1(f):
    return f["hashes"]["sha1"] if "hashes" in f else f["sha1"]


def _url(f):
    return f["downloads"][0] if "downloads" in f else f["url"]


def _leer_jar(datos: bytes) -> list:
    """[(id, version, depends)] del jar Y de los que lleva dentro.

    Los dos matices que convierten esta comprobacion en util o en ruido:

      provides  un mod puede declarar alias. `balm` declara `balm-fabric`, y
                Fabric API declara sus ~40 submodulos. Sin esto, medio pack
                parece que le falten dependencias.
      jars      **jar-in-jar**: xaerolib, kirin u owo-lib no son ficheros
                sueltos, viajan DENTRO del jar que los usa. Sin abrirlos, otra
                tanda de falsos positivos.
    """
    z = zipfile.ZipFile(io.BytesIO(datos))
    m = json.loads(z.read("fabric.mod.json"), strict=False)
    version = m.get("version")
    salida = [(m.get("id"), version, m.get("depends") or {})]
    for alias in m.get("provides", []):
        salida.append((alias, version, {}))
    for anidado in m.get("jars", []):
        try:
            salida += _leer_jar(z.read(anidado["file"]))
        except Exception:
            pass  # un JiJ ilegible no invalida el jar que lo contiene
    return salida


def metadatos_de(ficheros: list) -> dict:
    """{sha1: [[id, version, depends], ...]} de cada mod, leido de su jar."""
    try:
        cache = json.loads(CACHE_META.read_text(encoding="utf-8"))
    except Exception:
        cache = {}
    nuevos = 0
    for f in ficheros:
        if _sha1(f) in cache or not f["path"].startswith("mods/"):
            continue
        try:
            cache[_sha1(f)] = _leer_jar(bajar(_url(f)))
        except Exception:
            cache[_sha1(f)] = []
        nuevos += 1
    if nuevos:
        SALIDA.mkdir(parents=True, exist_ok=True)
        CACHE_META.write_text(json.dumps(cache), encoding="utf-8")
        print(f"  (leidos {nuevos} jars nuevos para comprobar dependencias)")
    return cache


def _num(v: str) -> tuple:
    """'0.116.8+1.21.1' -> (0, 116, 8). Lo de despues del + es metadato."""
    base = re.split(r"[+\-]", str(v))[0]
    partes = []
    for p in base.split("."):
        partes.append(int(p) if p.isdigit() else 0)
    return tuple(partes + [0] * (4 - len(partes)))[:4]


def cumple(version: str, rango) -> bool:
    """¿`version` satisface `rango` de Fabric? Subconjunto pragmatico.

    Ante un formato que no se sabe leer devuelve True: el objetivo es cazar el
    caso claro (una version por debajo del minimo), no reimplementar el
    resolutor de Fabric y bloquear publicaciones por un falso positivo.
    """
    if isinstance(rango, list):
        return any(cumple(version, r) for r in rango)
    r = str(rango).strip()
    if r in ("*", ""):
        return True
    if r.startswith(">="):
        return _num(version) >= _num(r[2:])
    if r.startswith(">"):
        return _num(version) > _num(r[1:])
    if r.endswith(".x"):
        pedido = _num(r[:-2])
        n = len(r[:-2].split("."))
        return _num(version)[:n] == pedido[:n]
    if r.startswith("~"):   # mismo major.minor, patch por encima
        return _num(version)[:2] == _num(r[1:])[:2] and _num(version) >= _num(r[1:])
    if r.startswith("^"):   # mismo major
        return _num(version)[:1] == _num(r[1:])[:1] and _num(version) >= _num(r[1:])
    return True


def verificar_dependencias(ficheros: list) -> None:
    """Aborta si un mod pide algo que el pack no le da."""
    meta = metadatos_de(ficheros)
    # Un mismo modulo puede venir en varios jars a la vez (cloth-config suelto y
    # ademas incrustado dentro de otro mod). Fabric carga **la version mas
    # alta**, asi que hay que quedarse con esa: comparar con la ultima leida da
    # un fallo inventado que no existe en el juego.
    presentes = {}
    for f in ficheros:
        for mid, version, _ in meta.get(_sha1(f)) or []:
            if not mid:
                continue
            if mid not in presentes or _num(version or "0") > _num(presentes[mid]):
                presentes[mid] = version or "0"

    problemas = []
    for f in ficheros:
        for mid, version, depends in meta.get(_sha1(f)) or []:
            for dep, rango in (depends or {}).items():
                if dep in LO_PONE_EL_JUEGO:
                    continue
                if dep not in presentes:
                    problemas.append(f"{mid} {version} necesita '{dep}' {rango} "
                                     f"y NO esta en el pack")
                elif not cumple(presentes[dep], rango):
                    problemas.append(f"{mid} {version} necesita {dep} {rango} "
                                     f"y el pack trae {presentes[dep]}")

    if problemas:
        print("\n  *** EL PACK NO ARRANCARIA ***")
        for p in problemas:
            print(f"    {p}")
        raise SystemExit(
            "\n  No se genera nada. Esto es exactamente lo que el jugador veria "
            "como\n  'Incompatible mods found!' al darle a Jugar. Arreglalo con "
            "SUBIR o EXCLUIDOS.")
    print(f"  dependencias: {len(presentes)} mods, todas satisfechas")


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


def propios():
    """Los jars de nuestros mods de cliente, ya compilados."""
    salida = []
    for carpeta, prefijo in PROPIOS:
        libs = RAIZ / carpeta / "build" / "libs"
        jars = [j for j in libs.glob(f"{prefijo}-*.jar")
                if not j.stem.endswith(("-sources", "-dev"))]
        if not jars:
            raise SystemExit(f"No hay jar de {prefijo} en {libs}.\n"
                             f"    cd {carpeta} && bash build.sh")
        salida.append(max(jars, key=lambda j: j.stat().st_mtime))
    return salida


def construir(nombre_pack, extra, sufijo, resumen):
    ficheros_base, overrides, z = base()
    ficheros, total = [], 0

    for f in ficheros_base:
        total += f["fileSize"]
        ficheros.append({k: f[k] for k in
                         ("path", "hashes", "env", "downloads", "fileSize")})

    for slug in extra:
        v = version_de(slug)
        if not v:
            print(f"  AVISO: {slug} no tiene version para {MC}, se omite")
            continue
        f = v["files"][0]
        total += f["size"]
        ficheros.append({
            "path": f"mods/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            # Todo lo que anadimos nosotros es de cliente: son herramientas de
            # construccion y efectos graficos.
            "env": {"client": "required", "server": "unsupported"},
            "downloads": [f["url"]],
            "fileSize": f["size"]})
        print(f"  extra  {slug:<22} {v['version_number']}")

    for slug in SHADERS:
        v = version_de(slug, loader=None)
        if not v:
            raise SystemExit(f"{slug} no tiene version para {MC}")
        f = v["files"][0]
        total += f["size"]
        ficheros.append({
            "path": f"shaderpacks/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": "required", "server": "unsupported"},
            "downloads": [f["url"]],
            "fileSize": f["size"]})
        print(f"  shader {slug:<22} {v['version_number']}")

    verificar_dependencias(ficheros)

    index = {
        "formatVersion": 1, "game": "minecraft", "versionId": "0.1.0",
        "name": nombre_pack,
        "summary": resumen,
        "files": ficheros,
        "dependencies": {"minecraft": MC, "fabric-loader": loader_estable()}}

    SALIDA.mkdir(parents=True, exist_ok=True)
    destino = SALIDA / f"PokeReport-LunaEternal{sufijo}-0.1.0.mrpack"
    with zipfile.ZipFile(destino, "w", zipfile.ZIP_DEFLATED) as salida:
        salida.writestr("modrinth.index.json",
                        json.dumps(index, indent=2, ensure_ascii=False))
        salida.writestr("overrides/servers.dat", servers_dat(*SERVIDOR))
        salida.writestr("overrides/config/iris.properties", IRIS_PROPERTIES)
        for n in overrides:
            salida.writestr(n, z.read(n))
        # Nuestros mods van DENTRO del zip, no por URL: no estan en Modrinth.
        # Es lo unico que este pack redistribuye, y es nuestro.
        for jar in propios():
            salida.write(jar, f"overrides/mods/{jar.name}")

    print(f"  -> {destino.name}  ({len(ficheros)} ficheros, "
          f"{len(overrides)} de config, {total // 1048576} MB)\n")


def main():
    print(f"BASE: {PACK_BASE}")
    print("\nPACK DE JUGADOR")
    construir("PokeReport: Luna Eternal", EXTRA_JUGADOR, "",
              "Cliente oficial, sobre el modpack oficial de Cobblemon.")
    print("PACK DE CONSTRUCTOR")
    construir("PokeReport: Luna Eternal (Constructor)",
              EXTRA_JUGADOR + EXTRA_CONSTRUCTOR, "-Constructor",
              "Como el oficial, mas WorldEdit CUI, Axiom y Litematica.")


if __name__ == "__main__":
    main()
