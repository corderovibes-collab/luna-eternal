"""Genera el catalogo de cosmeticos y sus assets, LEYENDO LOS PACKS.

    python tools/gen_catalogo_cosmeticos.py

Sin argumentos: los packs, sus URLs y sus huellas estan en
`tools/packs_cosmeticos.json`, y se bajan solos a `build/packs-cosmeticos/`.

Produce:

    mod/.../cosmetics/CatalogoMascotas.java              los disfraces de Pokemon
    mod/.../cosmetics/CatalogoSombreros.java             los sombreros del jugador
    neon/src/main/resources/resourcepacks/cosmeticos/    los assets, incrustados

⚠⚠ POR QUE SE GENERA, Y NO ES COMODIDAD

El catalogo estuvo escrito a mano y fallo TRES VECES SEGUIDAS. Las tres con el
mismo sintoma --se cobraba el cosmetico y salia el Pokemon normal-- y por causas
distintas, y ninguna dio el menor error:

  1. Se copio del repositorio de GitHub (HEAD), que declara los cosmeticos como
     `species_features`; la version PUBLICADA usa `cosmetic_items`.
  2. `26sinnohbundle` declara seis cosmeticos cuyo arte SE VENDE APARTE, y
     `pangoro_operator.json` pone `"pokemon": ["operator"]`, que es una errata.
  3. `pangoro_operator` tenia resolver, modelo y textura pero NO POSER, y
     Cobblemon no cae al poser de la especie: dibuja un bulto sin forma.

Hoy se exige que existan TODAS las piezas, y lo que un pack declara pero no puede
dibujar se imprime al terminar en vez de acabar en la tienda.

⚠ EL data/ DE LOS PACKS NO SE INSTALA EN NINGUN SITIO, Y ES DELIBERADO. Ahi viven
  las recetas y los `cosmetic_items`, que son las dos vias por las que un
  cosmetico se conseguiria JUGANDO: craftear el objeto y darselo al Pokemon.
  D-039 dice que solo se consiguen con LunaCoins o en eventos, asi que esa puerta
  no se vigila -- se quita. Ver docs/ui/cosmeticos.md §5-ter.
"""

import hashlib

import json
import shutil
import urllib.request
import zipfile
from pathlib import Path




RAIZ = Path(__file__).resolve().parent.parent
FUENTES = RAIZ / "tools" / "packs_cosmeticos.json"
CACHE = RAIZ / "build" / "packs-cosmeticos"
JAVA = RAIZ / "mod" / "src" / "main" / "java" / "net" / "pokereport" / "luna" / "cosmetics"
ASSETS = RAIZ / "neon" / "src" / "main" / "resources" / "resourcepacks" / "cosmeticos"

# `dex/` y `msd/` son variantes del MISMO cosmetico para formas concretas
# (megas). Contarlas daria tres Charizard `knight` en la rejilla. `mega/` es la
# carpeta donde Cosmetic Expansion mete las megaevoluciones enteras.
SUBCARPETAS_VARIANTES = ("dex", "msd", "mega")

# ⚠⚠ ASPECTOS QUE SON UN CAMBIO DE FORMA, NO UN DISFRAZ.
#
#   Lo reporto el usuario: «agregaste mega evoluciones pero en realidad no son
#   cosmeticos, son evoluciones». Y tenia razon: Cosmetic Expansion declara
#   `mega`, `mega_x` y `mega_y` como aspectos sueltos --el mismo mecanismo que un
#   disfraz-- porque necesita el modelo de la forma mega para poder dibujar SUS
#   cosmeticos encima de ella.
#
#   Vender eso seria vender una megaevolucion por 4.000 LunaCoins, que no es un
#   cosmetico: es progresion, y la progresion NO SE VENDE (P4, D-014).
#
#   ⚠ NO se excluye por contener "mega": `mega_glove` (un guante de Lucario) y
#     `armor_evo`/`armored` (la armadura de Mewtwo) SI son cosmeticos. La lista es
#     de formas EXACTAS a proposito -- una heuristica por subcadena se llevaria
#     por delante tres cosmeticos legitimos.
FORMAS_NO_COSMETICAS = {"mega", "mega_x", "mega_y", "gmax", "gigantamax", "eternamax"}

# Precios PROVISIONALES por tramos. CLAUDE.md dice que la economia se calibra con
# datos reales; esto solo reparte para que la tienda no tenga todo al mismo
# precio, que es lo unico que se sabe seguro que estaria mal.
TRAMOS = {"legendario": 4000, "inicial": 2500, "normal": 1500, "sombrero": 1200}
LEGENDARIOS = {
    "articuno", "zapdos", "moltres", "mewtwo", "mew", "raikou", "entei",
    "suicune", "lugia", "ho_oh", "ho-oh", "celebi", "rayquaza", "kyogre",
    "groudon", "dialga", "palkia", "giratina", "arceus", "darkrai",
}
INICIALES = {
    "charizard", "blastoise", "venusaur", "typhlosion", "feraligatr",
    "meganium", "blaziken", "swampert", "sceptile", "infernape", "empoleon",
    "torterra", "decidueye", "incineroar", "primarina", "cinderace",
    "rillaboom", "inteleon", "greninja", "ceruledge",
}


def precio(especie: str) -> int:
    if especie in LEGENDARIOS:
        return TRAMOS["legendario"]
    if especie in INICIALES:
        return TRAMOS["inicial"]
    return TRAMOS["normal"]


# --------------------------------------------------------------- descarga


def huella(fichero: Path) -> str:
    return hashlib.sha1(fichero.read_bytes()).hexdigest()


def bajar(pack: dict) -> Path:
    """El zip del pack, de la cache o del CDN, con la huella comprobada.

    ⚠ LA HUELLA NO ES BUROCRACIA: es lo que hace REPRODUCIBLE el catalogo. Sin
      ella, regenerar dentro de seis meses puede bajar otra version del pack,
      producir un catalogo distinto, y que la diferencia parezca un cambio
      nuestro. Se aborta en vez de seguir.
    """
    CACHE.mkdir(parents=True, exist_ok=True)
    destino = CACHE / ("%s.zip" % pack["nombre"].replace(" ", "_"))
    if destino.exists() and huella(destino) == pack["sha1"]:
        return destino

    print("  bajando %s %s..." % (pack["nombre"], pack["version"]))
    peticion = urllib.request.Request(
        pack["url"], headers={"User-Agent": "luna-eternal/0.1 (dev)"})
    datos = urllib.request.urlopen(peticion, timeout=180).read()
    real = hashlib.sha1(datos).hexdigest()
    if real != pack["sha1"]:
        raise SystemExit(
            "La huella de %s no cuadra.\n"
            "  esperada %s\n  recibida %s\n\n"
            "O el pack ha cambiado en el CDN, o la descarga vino mal. Si es lo\n"
            "primero, actualiza tools/packs_cosmeticos.json A CONCIENCIA: una\n"
            "version nueva puede quitar cosmeticos que alguien YA HA COMPRADO."
            % (pack["nombre"], pack["sha1"], real))
    destino.write_bytes(datos)
    return destino


# --------------------------------------------------------------- mascotas


def declarados_en(z) -> set:
    """Lo que el pack DICE que tiene, para poder avisar de lo que no cumple."""
    declarados = set()
    for n in z.namelist():
        if "/cosmetic_items/" not in n or not n.endswith(".json"):
            continue
        datos = json.loads(z.read(n))
        for especie in datos.get("pokemon", []):
            # ⚠ Algunas vienen con calificador de forma:
            #   "articuno galarian=false". La especie es lo de antes del espacio.
            especie = especie.split()[0].strip().lower()
            for ci in datos.get("cosmeticItems", []):
                if len(ci.get("aspects", [])) == 1 and especie:
                    declarados.add((especie, ci["aspects"][0]))
    return declarados


def leer_mascotas(zips):
    """(especie, aspecto) de los RESOLVERS. Devuelve `(filas, descartados)`.

    Un resolver ES el dibujo: si esta, se puede pintar, y lleva la especie de
    verdad. Es la unica fuente que no puede mentir sobre lo que el jugador vera.
    """
    filas, descartados = set(), []

    for nombre, z in zips:
        posers = {n.rsplit("/", 1)[-1][:-5] for n in z.namelist()
                  if "/posers/" in n and n.endswith(".json")}
        hojas = {n.rsplit("/", 1)[-1] for n in z.namelist()}
        dibujables = set()

        for n in z.namelist():
            if "/resolvers/" not in n or not n.endswith(".json"):
                continue
            # ⚠ SE MIRAN TODOS LOS TRAMOS, no solo el ultimo. Estaba mirando
            #   solo el ultimo, y `cosmetic/mega/0150_mewtwo` acaba en
            #   `0150_mewtwo`: las megaevoluciones de Cosmetic Expansion se
            #   colaron enteras y llegaron a la tienda.
            tramos = n.split("/resolvers/")[1].split("/")[:-1]
            if any(x in SUBCARPETAS_VARIANTES for x in tramos):
                continue
            datos = json.loads(z.read(n))
            especie = datos.get("species", "").split(":")[-1].strip().lower()
            if not especie:
                continue
            for var in datos.get("variations", []):
                aspectos = var.get("aspects", [])
                # Un solo aspecto: los de varios son `["magma","female"]`, o sea
                # el mismo cosmetico sobre otra forma. Y con modelo: una variacion
                # sin el es un recoloreado que hereda el del cosmetico base.
                if len(aspectos) != 1 or not var.get("model"):
                    continue
                if aspectos[0] in FORMAS_NO_COSMETICAS:
                    descartados.append((nombre, especie, aspectos[0], "es una FORMA, no un disfraz"))
                    continue
                poser = (var.get("poser") or "").split(":")[-1]
                # Solo se rechaza si el pack nombra un poser SUYO y no lo trae:
                # uno del juego base siempre existe.
                if poser and poser not in posers and poser.endswith(aspectos[0]):
                    descartados.append((nombre, especie, aspectos[0], "sin poser"))
                    continue
                textura = (var.get("texture") or "").rsplit("/", 1)[-1]
                if textura and (textura + ".png") not in hojas and textura not in hojas:
                    descartados.append((nombre, especie, aspectos[0], "sin textura"))
                    continue
                dibujables.add((especie, aspectos[0]))

        for especie, aspecto in sorted(declarados_en(z) - dibujables):
            descartados.append((nombre, especie, aspecto, "sin arte"))
        filas |= dibujables

    return sorted(filas), descartados


# -------------------------------------------------------------- sombreros


def declarados_sombreros(z, estilo: str) -> set:
    """Que modelos DECLARA el pack como sombrero ponible.

    ⚠⚠ NO VALE COGER TODO JSON CON `elements`. Los packs traen SUBMODELOS --la
       segunda capa de un sombrero, una pieza suelta-- que son json de modelo
       perfectamente validos y NO son un sombrero. Cogiendolos salen `dawn_hat` y
       `dawn_hat2` como dos articulos distintos, y el segundo es medio gorro.

       Es la misma leccion de las mascotas, en su cuarta vuelta: hay que leer lo
       que el pack DECLARA... y despues comprobar que se puede dibujar. Ninguna de
       las dos cosas basta sola.

    ⚠ CADA PACK LO DECLARA A SU MANERA, Y NO SE PUEDE ADIVINAR. Por eso el estilo
      esta ESCRITO en packs_cosmeticos.json en vez de deducirse: una heuristica
      que acierta con tres packs falla con el cuarto, y falla en silencio.

        objeto      un OBJETO en la cabeza. CobbleHats con los `overrides` de
                    `carved_pumpkin.json`, Accessories con `.properties` de CIT
        simplehats  todo modelo cuyo `parent` sea `simplehats:item/hatparent`.
                    Es lo que separa sus 332 sombreros de sus bolsas y recortes
    """
    declarados = set()

    if estilo == "simplehats":
        for n in z.namelist():
            if "/models/item/" not in n or not n.endswith(".json"):
                continue
            try:
                d = json.loads(z.read(n))
            except Exception:
                continue
            if "hatparent" in str(d.get("parent", "")):
                declarados.add(n.rsplit("/", 1)[-1][:-5])
        return declarados

    # CobbleHats: los overrides de la calabaza tallada.
    for n in z.namelist():
        if not n.endswith("/models/item/carved_pumpkin.json"):
            continue
        for ov in json.loads(z.read(n)).get("overrides", []):
            modelo = str(ov.get("model", "")).rsplit("/", 1)[-1]
            if modelo:
                declarados.add(modelo)

    # Accessories: los .properties de CIT Resewn.
    for n in z.namelist():
        if not n.endswith(".properties") or "/cit" not in n:
            continue
        for linea in z.read(n).decode("utf8", "ignore").splitlines():
            if linea.strip().startswith("model="):
                modelo = linea.split("=", 1)[1].strip().lstrip("./")
                if modelo:
                    declarados.add(modelo.rsplit("/", 1)[-1])
    return declarados


def nombres_de(z) -> dict:
    """Los nombres de verdad, del `lang` del pack si lo trae.

    Simple Hats llama `acornhat` a lo que enseña como "Acorn Cap". Sin esto la
    tienda seria una lista de identificadores pegados, que es lo que pasa con los
    46 de los otros dos packs -- "Alolan digglethat" -- porque ESOS no traen
    nombres y no hay de donde sacarlos.
    """
    for n in z.namelist():
        if n.endswith("/lang/en_us.json"):
            try:
                lang = json.loads(z.read(n))
            except Exception:
                return {}
            return {k.rsplit(".", 1)[-1]: v for k, v in lang.items()
                    if k.startswith("item.")}
    return {}


def leer_sombreros(zips):
    """Los sombreros: su modelo json, su textura y su nombre.

    ⚠⚠ NINGUN PACK SE USA COMO SUS AUTORES LO PENSARON, Y ES A PROPOSITO.
       CobbleHats los aplica con una CALABAZA TALLADA + CustomModelData,
       Accessories con CIT Resewn sobre un casco, y Simple Hats con un OBJETO en
       una ranura de accesorios. Las tres formas son un objeto que el jugador
       lleva encima, y eso significa:

         - ocupa una ranura (casco, o la de accesorios de otro mod)
         - se cae al morir, se comercia, se pierde
         - y sobre todo SE PUEDE REGALAR, asi que el cosmetico dejaria de venir
           solo de LunaCoins o de eventos, que es lo que dice D-039

       Lo que se aprovecha son sus MODELOS. El sombrero lo dibuja el cliente
       sobre la cabeza y no existe objeto ninguno: el servidor dice quien lleva
       cual, igual que con las auras. Simple Hats ni siquiera se instala como mod.

    Devuelve ([(id, modelo_dict, bytes_textura, nombre, pack)], descartados).
    """
    salida, fuera = [], []
    for nombre, z, estilo in zips:
        declarados = declarados_sombreros(z, estilo)
        nombres = nombres_de(z)

        # ⚠ SOLO BAJO `models/` O `cit/`. El nombre de un modelo y el de SU RECETA
        #   son el mismo --`models/block/custom/blaziken_cap.json` y
        #   `data/crafting/recipe/blaziken_cap.json`-- asi que indexando por la
        #   hoja del nombre, la receta PISA al modelo. El sintoma era "el modelo
        #   no tiene geometria" en ocho sombreros que la tienen perfectamente.
        modelos = {}
        for n in z.namelist():
            if not n.endswith(".json"):
                continue
            if "/models/" not in n and "/cit" not in n:
                continue
            hoja = n.rsplit("/", 1)[-1][:-5]
            if hoja in declarados:
                modelos[hoja] = n

        for ident in sorted(declarados):
            n = modelos.get(ident)
            if not n:
                fuera.append((nombre, ident, "el modelo no viene"))
                continue
            try:
                d = json.loads(z.read(n))
            except Exception:
                fuera.append((nombre, ident, "el modelo no se puede leer"))
                continue
            if not d.get("elements"):
                fuera.append((nombre, ident, "el modelo no tiene geometria"))
                continue
            tex = None
            for valor in (d.get("textures") or {}).values():
                hoja = str(valor).rsplit("/", 1)[-1]
                for x in z.namelist():
                    if x.endswith("/" + hoja + ".png"):
                        tex = x
                        break
                if tex:
                    break
            if not tex:
                fuera.append((nombre, ident, "sin textura"))
                continue
            salida.append((ident, d, z.read(tex),
                           nombres.get(ident) or bonito(ident), nombre))

    # Sin identificadores repetidos entre packs, y en orden estable para que el
    # fichero generado no cambie de un dia para otro sin motivo.
    vistos, unicos = set(), []
    for s in sorted(salida, key=lambda x: x[0]):
        if s[0] in vistos:
            fuera.append((s[4], s[0], "identificador repetido en otro pack"))
            continue
        vistos.add(s[0])
        unicos.append(s)
    return unicos, fuera


def padres_de(zips) -> dict:
    """Los modelos PADRE que los sombreros necesitan, ya listos para copiar.

    ⚠ Simple Hats mete las transformaciones --entre ellas la de `head`, que es la
      que coloca el sombrero sobre la cabeza-- en dos modelos padre compartidos,
      `hatparent` y `hatparent2`. Sin copiarlos, sus 332 sombreros heredarian de
      algo que no existe y no se dibujaria ninguno.
    """
    padres = {}
    for _, z, estilo in zips:
        if estilo != "simplehats":
            continue
        for n in z.namelist():
            hoja = n.rsplit("/", 1)[-1]
            if "/models/item/" in n and hoja.startswith("hatparent"):
                padres[hoja[:-5]] = json.loads(z.read(n))
    return padres


# ----------------------------------------------------------------- assets


def copiar_assets(zips, sombreros, padres):
    """Los assets de los packs, al resource pack incrustado en `lunaneon`.

    ⚠ SOLO `assets/`. El `data/` se queda fuera a proposito -- ver la cabecera.

    ⚠ Los sombreros se reescriben a NUESTRO espacio de nombres. Sus modelos
      apuntan a texturas de `minecraft:`, y dejarlos asi PISARIA texturas del
      juego base: `assets/minecraft/textures/block/custom/...` no colisiona hoy,
      pero un pack que use ese nombre repintaria un bloque de verdad. Bajo
      `lunaeternal:` no puede pasar.
    """
    if ASSETS.exists():
        shutil.rmtree(ASSETS)
    ASSETS.mkdir(parents=True)

    copiados = 0
    for _, z, _ in zips:
        for n in z.namelist():
            # Los de `minecraft:` son los sombreros y se tratan aparte, abajo.
            if not n.startswith("assets/cobblemon/") or n.endswith("/"):
                continue
            destino = ASSETS / n
            destino.parent.mkdir(parents=True, exist_ok=True)
            destino.write_bytes(z.read(n))
            copiados += 1

    base = ASSETS / "assets" / "lunaeternal"
    # ⚠ LOS PADRES PRIMERO. Simple Hats mete la transformacion `head` --la que
    #   coloca el sombrero sobre la cabeza-- en dos modelos padre compartidos.
    #   Sin copiarlos, sus 332 sombreros heredarian de algo que no existe.
    for ident, d in padres.items():
        (base / "models" / "sombreros").mkdir(parents=True, exist_ok=True)
        (base / "models" / "sombreros" / (ident + ".json")).write_text(
            json.dumps(d, indent=1), encoding="utf-8")
        copiados += 1

    for ident, modelo, tex, _, _ in sombreros:
        d = dict(modelo)
        # ⚠⚠ LA TEXTURA TIENE QUE VIVIR BAJO `textures/item/`, Y NO ES ESTILO.
        #
        #   El atlas de bloques --que es de donde los modelos de objeto sacan sus
        #   sprites-- se construye con DOS fuentes de tipo `directory`:
        #
        #       {"type":"directory","source":"block","prefix":"block/"}
        #       {"type":"directory","source":"item", "prefix":"item/"}
        #
        #   (leido de assets/minecraft/atlases/blocks.json del jar de 1.21.1).
        #   Recorren TODOS los espacios de nombres, pero SOLO esas dos carpetas.
        #
        #   Estaban en `textures/sombreros/`, o sea fuera de las dos: no se
        #   cosian al atlas, y el modelo se quedaba con el sprite de "falta
        #   esto". El sintoma es un bloque NEGRO Y MAGENTA, que parece una
        #   textura perdida --y lo es, pero no porque el fichero falte: esta ahi
        #   y en el jar, solo que en una carpeta que nadie mira--.
        #
        #   Un pack NO puede añadir fuentes al atlas sin SOBRESCRIBIR
        #   `blocks.json` entero, y eso pisaria el de vanilla. Meterlas en
        #   `item/` usa la fuente que ya existe y no sobrescribe nada.
        d["textures"] = {k: "lunaeternal:item/sombreros/" + ident
                         for k in (d.get("textures") or {})}

        # ⚠⚠ SE QUITA EL `parent`, Y NO ES LIMPIEZA: SIN ESTO EL MODELO NO CARGA.
        #
        #   Cinco de los seis modelos de Cobblemon Accessories traen
        #   `"parent": "ash_journey"` --un nombre SUELTO, sin espacio de
        #   nombres--, que Minecraft resuelve como `minecraft:ash_journey` y no
        #   existe. Son restos de Blockbench, o apuntan a los SUBMODELOS que no
        #   copiamos (`lilie_hat3`, `dawn_hat2`).
        #
        #   Los 46 traen su propia geometria, su propio `display` y sus propias
        #   texturas, asi que el padre no aporta nada. Y el sintoma de dejarlo no
        #   es un error: es un sombrero que no se dibuja.
        # ⚠ EL `parent` SE REESCRIBE O SE QUITA, SEGUN LO QUE APUNTE.
        #
        #   Simple Hats lo usa DE VERDAD: sus sombreros no traen transformaciones
        #   propias, las heredan de `hatparent`. Quitarselo los dejaria sin la de
        #   `head` y saldrian en el sitio equivocado.
        #
        #   Los cinco de Cobblemon Accessories lo traen como resto de Blockbench
        #   --`"parent": "ash_journey"`, un nombre SUELTO sin espacio de nombres,
        #   que Minecraft resuelve como `minecraft:...` y no existe-- o apuntando
        #   a submodelos que no copiamos. Esos si se quitan: traen su propia
        #   geometria y su propio `display`.
        #
        #   Distinguirlos por el nombre del padre y no por el pack: lo que importa
        #   es si ESE padre existe entre los que hemos copiado.
        padre = str(d.get("parent", "")).rsplit("/", 1)[-1]
        if padre in padres:
            d["parent"] = "lunaeternal:sombreros/" + padre
        else:
            d.pop("parent", None)
        (base / "models" / "sombreros").mkdir(parents=True, exist_ok=True)
        (base / "models" / "sombreros" / (ident + ".json")).write_text(
            json.dumps(d, indent=1), encoding="utf-8")
        (base / "textures" / "item" / "sombreros").mkdir(parents=True, exist_ok=True)
        (base / "textures" / "item" / "sombreros" / (ident + ".png")).write_bytes(tex)
        copiados += 2

    (ASSETS / "pack.mcmeta").write_text(json.dumps({
        "pack": {"pack_format": 34,
                 "description": "Cosmeticos de PokeReport Network"}
    }, indent=2) + "\n", encoding="utf-8")
    return copiados


# --------------------------------------------------------------- plantillas


CABECERA = '''package net.pokereport.luna.cosmetics;

import java.util.List;

import net.pokereport.luna.cosmetics.Catalogo.Pieza;

/**
 * %(titulo)s
 *
 * <p><b>ESTE FICHERO SE GENERA:</b> {@code python tools/gen_catalogo_cosmeticos.py}
 *
 * <p>Se genera <b>leyendo los packs</b>, que es lo unico que dice la verdad sobre
 * lo que se puede dibujar. Escribirlo a mano fallo tres veces, y las tres se
 * cobro un cosmetico que no se veia. Ver la cabecera del generador.
 *
 * <p>⚠ Los precios son por tramos y <b>PROVISIONALES</b>.
 */
public final class %(clase)s {

    private %(clase)s() {
    }

    static final List<Pieza> PIEZAS = List.of(
%(piezas)s
    );
}
'''


def escribir(clase, titulo, lineas):
    destino = JAVA / (clase + ".java")
    destino.write_text(CABECERA % {
        "clase": clase, "titulo": titulo, "piezas": ",\n".join(lineas),
    }, encoding="utf-8")
    return destino


# ------------------------------------------------------------------- main


def main() -> None:
    fuentes = json.loads(FUENTES.read_text(encoding="utf-8"))["packs"]
    zips = []
    for pack in fuentes:
        zips.append((pack["nombre"], zipfile.ZipFile(bajar(pack)),
                     pack.get("estilo", "objeto")))

    de_mascotas = [(n, z) for (n, z, _), p in zip(zips, fuentes)
                   if p["aporta"] == "mascotas"]
    de_sombreros = [(n, z, e) for (n, z, e), p in zip(zips, fuentes)
                    if p["aporta"] == "sombreros"]

    filas, descartados = leer_mascotas(de_mascotas)
    sombreros, sombreros_fuera = leer_sombreros(de_sombreros)
    padres = padres_de(de_sombreros)

    if not filas:
        raise SystemExit(
            "Ningun pack trae resolvers. O no son los packs, o cambiaron de "
            "estructura -- que ya paso una vez: el repositorio declara los "
            "cosmeticos como `species_features` y la version publicada usa "
            "`cosmetic_items`.")

    escribir("CatalogoMascotas", "Los disfraces de Pokemon.",
             ['            new Pieza("%s_%s", Catalogo.MASCOTAS, "cobblemon:%s", "%s", %d)'
              % (e, a, e, a, precio(e)) for e, a in filas])

    escribir("CatalogoSombreros", "Los sombreros que lleva el JUGADOR.",
             ['            new Pieza("sombrero_%s", Catalogo.SOMBREROS, "", "%s", %d)'
              % (i, nom.replace('"', "'"), TRAMOS["sombrero"])
              for i, _, _, nom, _ in sombreros])

    copiados = copiar_assets(zips, sombreros, padres)

    print("mascotas   %3d  (%d especies)" % (len(filas), len({f[0] for f in filas})))
    print("sombreros  %3d" % len(sombreros))
    print("assets     %3d ficheros -> %s" % (copiados, ASSETS))

    if descartados:
        print("\nFUERA (%d): declarados pero NO dibujables." % len(descartados))
        print("Se venderian y no se veria nada, que es lo que ya paso.")
        for pack, especie, aspecto, motivo in descartados:
            print("   %-30s %-14s %-14s %s" % (pack[:30], especie, aspecto, motivo))
    if sombreros_fuera:
        print("\nSOMBREROS FUERA (%d):" % len(sombreros_fuera))
        for pack, ident, motivo in sombreros_fuera:
            print("   %-30s %-24s %s" % (pack[:30], ident, motivo))

    print("\nAVISO: los precios son por tramos y PROVISIONALES.")


def bonito(ident: str) -> str:
    """`sylveonears` -> `Sylveonears`; `alolan_digglethat` -> `Alolan digglethat`.

    No se intenta separar palabras pegadas: `sylveonears` no se puede partir sin
    una lista de nombres, y una heuristica acertaria unas veces y otras no --que
    es peor que ser consistente--.
    """
    s = ident.replace("_", " ").strip()
    return s[:1].upper() + s[1:] if s else ident


if __name__ == "__main__":
    main()
