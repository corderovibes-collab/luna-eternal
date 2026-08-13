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
import io, json, os, re, struct, urllib.parse, urllib.request, zipfile
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
PACK_BASE = "cobblemon-fabric"          # "Cobblemon Official Modpack [Fabric]"

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
}

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
        "y quedarnos sin luz de color en los neones",
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
    # Luz de color sin shaders. La luz de Minecraft no tiene color (guarda un
    # numero 0-15), asi que un neon cian iluminaria en blanco sin esto.
    "shine",
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
    d = api(q)
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
    return ficheros, overrides, z


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
    print(f"BASE: modpack oficial de Cobblemon ({PACK_BASE})")
    print("\nPACK DE JUGADOR")
    construir("PokeReport: Luna Eternal", EXTRA_JUGADOR, "",
              "Cliente oficial, sobre el modpack oficial de Cobblemon.")
    print("PACK DE CONSTRUCTOR")
    construir("PokeReport: Luna Eternal (Constructor)",
              EXTRA_JUGADOR + EXTRA_CONSTRUCTOR, "-Constructor",
              "Como el oficial, mas WorldEdit CUI, Axiom y Litematica.")


if __name__ == "__main__":
    main()
