"""Genera el catalogo de la tienda A PARTIR DE LOS JARS QUE CORREN DE VERDAD.

    python tools/gen_tienda.py
    python tools/gen_tienda.py --listar cobblefurnies
    python tools/gen_tienda.py --buscar eevee

POR QUE SE GENERA Y NO SE ESCRIBE
---------------------------------
Es la misma leccion que costo 62 cosmeticos que no existian: un catalogo
escrito a mano PROMETE cosas, y nadie comprueba que esten. `ShopCatalog.load()`
se salta los objetos que no existan con un aviso en el log --que nadie mira--,
asi que un identificador mal escrito no da error: da una tienda con un hueco.

Generarlo de los jars no puede prometer lo que no hay. Si un identificador no
existe, ESTE SCRIPT ABORTA y no se publica nada.

DE DONDE SALEN LOS JARS
-----------------------
⚠⚠⚠ DEL MANIFIESTO PUBLICADO, y se cachean en `build/tienda-jars/`. Antes este
script leia un `build/cobblemon-para-texturas.jar` que alguien habia dejado ahi
a mano y que NO ESTA EN GIT: en un clon limpio no se podia ejecutar, y eso es
exactamente la leccion de las seis pantallas en magenta --un generador que
depende de un fichero que no esta en el repositorio no se puede volver a
ejecutar--.

El manifiesto es ademas la fuente CORRECTA: es literalmente la lista de lo que
tiene el cliente. Un objeto que este ahi, esta en el juego.

⚠ Vainilla es la excepcion: no viaja en el manifiesto. Sale del jar de
Minecraft que deja Fabric Loom al compilar el mod, asi que hay que haber
compilado alguna vez (`mod/build.sh`), que es el primer paso de cualquier
cambio de todas formas.

LA TIENDA CRECE, Y ESO REVOCA UNA DECISION ANTERIOR
---------------------------------------------------
El 2026-08-23 el usuario la recorto a NUEVE articulos: «items basicos, los
necesarios... lo otro lo tienen que conseguir explorando». El 2026-09-04 pidio
SEIS APARTADOS NUEVOS: peluches, objetos de crianza, bayas, cultivos,
variedades y muebles.

⚠⚠ LO QUE SE DIJO ENTONCES SIGUE SIENDO CIERTO Y QUEDA ESCRITO: «las BAYAS y
   las BELLOTAS son justo lo que da XP al oficio AGRICULTOR, y la madera y la
   piedra lo del MINERO; venderlas competiria con ellos», y «una tienda
   completa VACIA EL MUNDO: si todo se compra, explorar solo sirve para
   conseguir dinero». Las categorias de BAYAS y CULTIVOS chocan de frente con
   eso, y es una decision del usuario tomada sabiendolo.

   ⚠ La palanca para corregirlo, si algun dia se nota, NO es quitar la
     categoria: es SUBIR SU ESCALON. Un cultivo caro sigue existiendo para
     quien tenga prisa y no compite con cosecharlo.

⚠⚠⚠ Y LOS OBJETOS DE CRIANZA VAN EN PLATA, NO EN LUNACOINS. El Destiny Knot y
   los seis objetos de poder son PROGRESION COMPETITIVA --deciden que IVs y que
   EVs hereda una cria-- y venderlos por moneda de pago es T4, la linea roja de
   D-007 y D-014. Por Plata son un sumidero y estan bien. Los PELUCHES si van
   en LunaCoins: son identidad pura (T1), que es venta libre.

LOS PRECIOS SON PROVISIONALES, Y A PROPOSITO
--------------------------------------------
Decision del usuario: «mas adelante definimos precios porque necesitamos hacer
un analisis general de la economia».

Por eso los precios no se escriben articulo a articulo: hay ESCALONES y cada
categoria dice a cual pertenece. Aplicar el analisis sera cambiar esas cifras.
Los anclajes salen de la configuracion real de produccion: Poke Ball 400,
Pocion 600, Superpocion 900, Revivir 3000.
"""

import argparse
import hashlib
import json
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
CACHE = RAIZ / "build" / "tienda-jars"
SALIDA = RAIZ / "mod" / "src" / "main" / "resources" / "data" / "lunaeternal" / "shop_catalog.json"
PUNTERO = ("https://github.com/corderovibes-collab/luna-eternal-pack"
           "/releases/download/pack-manifest/latest.json")

# ---------------------------------------------------------------- LOS PRECIOS
#
# ⚠ PROVISIONALES. Ver el docstring. Se retocan AQUI y en ningun otro sitio.
#
# La venta al banco es un PORCENTAJE del precio de compra, no un numero suelto:
# asi el invariante de no-arbitraje (vender por mas de lo que cuesta = dinero
# infinito) no puede romperse por un despiste al teclear.
ESCALONES = {
    "regalado":  50,    # <- decoracion suelta: una silla no vale una Poke Ball
    "barato":   120,
    "basico":   200,
    "comun":    400,    # <- ancla: Poke Ball, de la config real de produccion
    "medio":    600,    # <- ancla: Pocion
    "bueno":    900,    # <- ancla: Superpocion
    "raro":   3_000,    # <- ancla: Revivir
}
RECOMPRA = 0.10          # el banco paga el 10 % . tambien provisional

# ⚠⚠ LOS LUNACOINS VAN APARTE Y NO SE CONVIERTEN. No es «lo mismo en otra
#    escala»: D-014 dice que NINGUNA moneda se convierte en otra, en ninguna
#    direccion, y de esa unica regla salen las tres garantias del modelo. Poner
#    aqui un «precio en Plata equivalente» seria escribir el tipo de cambio que
#    esa regla prohibe.
ESCALONES_LUNA = {
    "peluche": 40,
    # ⚠⚠ LA ULTIMA PROTECCION, Y SOLO ESA (decision del usuario, 2026-09-04):
    #    «solo la ultima proteccion va a valer lunacoins y de resto lo otro va
    #    a valer plata». 251x251 son 63.001 bloques: es el techo del sistema.
    "p251": 500,
}

# ⚠⚠⚠ LAS PROTECCIONES TIENEN SU PROPIA ESCALA, Y NO ES UN CAPRICHO. El escalon
#    mas caro de la tienda son 3.000 (Revivir), y una parcela no es un objeto de
#    combate: es EL MAYOR SUMIDERO DEL JUEGO (P3). Con precios de objeto, todo
#    el mundo tendria las cinco la primera semana y el dinero dejaria de valer.
#
# ⚠⚠ LA CURVA SUBE MAS QUE EL AREA, a proposito. De 15x15 a 51x51 el area se
#    multiplica por 11,6 y el precio por 4; de 101 a 161 el area por 2,5 y el
#    precio por 2,5. Si el precio subiera IGUAL que el area, la grande seria
#    siempre el mejor trato por bloque y las pequeñas no las compraria nadie.
#    Aqui el bloque protegido sale mas barato cuanto mas grande la parcela, que
#    es lo que hace que valga la pena ahorrar, PERO la entrada cuesta.
#
# ⚠ PROVISIONALES como todo lo demas, y con una referencia para calibrarlos:
#   las cazas dan ~10.000 de Plata al dia si se completan las seis, asi que la
#   primera proteccion es medio dia y la de 161x161 son dos semanas.
PROTECCIONES = {
    "p15":     5_000,
    "p51":    20_000,
    "p101":   60_000,
    "p161":  150_000,
}

# ------------------------------------------------------------- LOS JARS
#
# namespace -> con que empieza el nombre del jar en el manifiesto
FUENTES = {
    "cobblemon":     "Cobblemon-fabric",
    "pokeblocks":    "pokeblocks",
    "cobblefurnies": "CobbleFurnies",
}

# ------------------------------------------------------------- LAS CATEGORIAS
#
# Cada una lleva:
#   articulos   lista explicita de (id_completo, escalon, etiqueta)
#   patron      (namespace, regex, escalon) -- TODO lo que casa
#   moneda      "REPORTCOIN" si no es Plata
#
# ⚠⚠ UN PATRON EN VEZ DE 367 LINEAS, y no es pereza: una lista a mano se queda
#    vieja en cuanto el mod añade o quita un objeto, y no lo dice nadie. El
#    patron se resuelve contra el jar de verdad en cada ejecucion, y el
#    generador imprime cuantos ha encontrado -- que es la comprobacion.
CATEGORIAS = [
    {
        "id": "esencial",
        "nombre": "§cLo esencial",
        "icono": "cobblemon:poke_ball",
        "descripcion": "La Poké Ball normal y poco más. Lo demás se encuentra.",
        "articulos": [
            # ⚠ SOLO LA NORMAL. Ni Great, ni Ultra, ni las de bellota: esas se
            #   craftean con bellotas --que es justo lo que da XP al oficio
            #   AGRICULTOR-- o se encuentran. Venderlas competiria con jugar.
            ("cobblemon:poke_ball", "comun", None),
            # ⚠ ESTO NO ES UN OBJETO DE COMBATE, ES UN MATERIAL. La MAQUINA
            #   CURATIVA se craftea con cobre, hierro, redstone y UN MAX REVIVE
            #   (verificado en su receta), y el Max Revive NO se craftea: sale
            #   de cofres. Sin el, montarse la base es cuestion de suerte.
            ("cobblemon:max_revive", "raro",
             "§fMáx. Revivir §8· para la Máquina Curativa"),
        ],
    },
    {
        "id": "cuidado",
        "nombre": "§aCuidado",
        "icono": "cobblemon:potion",
        "descripcion": "Curar un poco y quitar estados. Lo del día a día.",
        "articulos": [
            # ⚠ LOS DOS QUE CAEN EN LA BANDA QUE PIDIO EL USUARIO (20-50 %), y
            #   los numeros salen de SUS DATOS, no de memoria:
            #   data/cobblemon/mechanics/potions.json dice 20 / 60 / 120 PS.
            #   La HIPERPOCION (120) cura una barra entera de casi cualquiera:
            #   por eso NO esta, y esa es la linea.
            ("cobblemon:potion", "medio", "§fPoción §8· cura 20 PS"),
            ("cobblemon:super_potion", "bueno", "§fSuperpoción §8· cura 60 PS"),
            ("cobblemon:antidote", "basico", None),
            ("cobblemon:burn_heal", "basico", None),
            ("cobblemon:ice_heal", "basico", None),
            ("cobblemon:paralyze_heal", "basico", None),
            ("cobblemon:awakening", "basico", None),
        ],
    },
    {
        "id": "crianza",
        "nombre": "§dCrianza",
        "icono": "cobblemon:destiny_knot",
        "descripcion": "Lo que decide qué hereda una cría. Se paga en Plata.",
        # ⚠⚠⚠ EN PLATA Y NO EN LUNACOINS, y no es un descuido: estos objetos
        #    deciden IVs y EVs, o sea PROGRESION COMPETITIVA. Venderlos por
        #    moneda de pago es T4, la linea roja de D-007 y D-014.
        "articulos": [
            ("cobblemon:destiny_knot", "raro",
             "§fLazo Destino §8· hereda 5 IVs de los padres"),
            ("cobblemon:everstone", "bueno",
             "§fPiedra Eterna §8· no evoluciona, y pasa su naturaleza"),
            ("cobblemon:oval_stone", "medio", None),
            ("cobblemon:link_cable", "medio", None),
            ("cobblemon:power_weight", "bueno", None),
            ("cobblemon:power_bracer", "bueno", None),
            ("cobblemon:power_belt", "bueno", None),
            ("cobblemon:power_lens", "bueno", None),
            ("cobblemon:power_band", "bueno", None),
            ("cobblemon:power_anklet", "bueno", None),
        ],
    },
    {
        "id": "bayas",
        "nombre": "§2Bayas",
        "icono": "cobblemon:oran_berry",
        "descripcion": "Todas las bayas. Cultivarlas sigue dando XP de Agricultor.",
        # ⚠⚠ COMPITE CON EL OFICIO AGRICULTOR, y esta puesto sabiendolo (ver el
        #    docstring). El escalon es la palanca si algun dia se nota.
        "patron": ("cobblemon", r"^[a-z]+_berry$", "basico"),
    },
    {
        "id": "cultivos",
        "nombre": "§6Cultivos",
        "icono": "minecraft:carrot",
        "descripcion": "Semillas y cosecha para empezar una huerta.",
        # ⚠ Lo unico de VAINILLA que se vende, y es la excepcion a «lo de
        #   Minecraft se consigue explorando»: son las semillas con las que se
        #   arranca, no el producto de una hora de juego.
        "articulos": [
            ("minecraft:wheat_seeds", "regalado", None),
            ("minecraft:beetroot_seeds", "regalado", None),
            ("minecraft:melon_seeds", "regalado", None),
            ("minecraft:pumpkin_seeds", "regalado", None),
            ("minecraft:carrot", "regalado", None),
            ("minecraft:potato", "regalado", None),
            ("minecraft:sugar_cane", "barato", None),
            ("minecraft:cactus", "barato", None),
            ("minecraft:bamboo", "barato", None),
        ],
    },
    {
        "id": "variedades",
        "nombre": "§bVariedades",
        "icono": "minecraft:water_bucket",
        "descripcion": "Cubos y utilidades sueltas que hacen falta a diario.",
        "articulos": [
            ("minecraft:bucket", "basico", None),
            ("minecraft:water_bucket", "medio", None),
            ("minecraft:lava_bucket", "bueno", None),
            ("minecraft:milk_bucket", "basico", "§fLeche §8· quita cualquier efecto"),
            ("minecraft:name_tag", "medio", None),
            ("minecraft:lead", "basico", None),
            ("minecraft:shears", "barato", None),
            ("minecraft:flint_and_steel", "barato", None),
        ],
    },
    {
        "id": "muebles",
        "nombre": "§eMuebles",
        "icono": "cobblefurnies:oak_chair",
        "descripcion": "Sillas, mesas, armarios y demás, en toda la madera.",
        # ⚠ TODO CobbleFurnies (MIT). Son 367 objetos: sin el buscador de la
        #   pantalla esto serian 74 paginas de flecha.
        "patron": ("cobblefurnies", r".*", "barato"),
    },
    {
        "id": "protecciones",
        "nombre": "§bProtecciones",
        "icono": "minecraft:beacon",
        "descripcion": "Nadie toca tu terreno. Colócala y ya está protegido.",
        # ⚠⚠⚠ LAS CINCO SON UN `minecraft:player_head`, y lo que las distingue
        #    es la etiqueta `protectionstones:stone_type` que lleva dentro. Por
        #    eso cada una trae `give`: la tienda NO fabrica el modulo, se lo
        #    pide a ClaimBlocks (`CBItemManager.getStone`), que es quien sabe
        #    como es. Dar la cabeza pelada no daria ningun error -- el jugador
        #    la colocaria y no pasaria nada.
        #    ⚠⚠ Y por eso `give` es ademas LA CLAVE de la entrada: con la
        #       busqueda por id del objeto, las cinco serian la misma y siempre
        #       ganaria la primera.
        #
        # ⚠⚠ NO SE RECOMPRAN. Un modulo colocado es una parcela: devolverlo al
        #    banco seria vender un terreno que sigue protegido. Se recupera
        #    borrando la proteccion desde su menu, que devuelve el modulo.
        "recompra": False,
        "articulos": [
            ("minecraft:player_head", "p15", "§fPoké Ball §8· protege 15×15",
             "claimblocks:template1"),
            ("minecraft:player_head", "p51", "§9Great Ball §8· protege 51×51",
             "claimblocks:template2"),
            ("minecraft:player_head", "p101", "§eUltra Ball §8· protege 101×101",
             "claimblocks:template3"),
            ("minecraft:player_head", "p161", "§6Luxury Ball §8· protege 161×161",
             "claimblocks:template4"),
            # ⚠ LA UNICA EN LUNACOINS, dicho por el usuario. Va en la entrada y
            #   no en la categoria: partir la escalera en dos categorias por la
            #   moneda romperia lo unico que la hace legible, que es verlas
            #   juntas y en orden.
            ("minecraft:player_head", "p251", "§5Master Ball §8· protege 251×251",
             "claimblocks:template5"),
        ],
    },
    {
        "id": "peluches",
        "nombre": "§5Peluches",
        "icono": "pokeblocks:pokedoll_eevee",
        "descripcion": "El peluche de tu Pokémon. Se pagan en LunaCoins.",
        # ⚠⚠⚠ LOS UNICOS EN LUNACOINS, y por eso se puede: un peluche es
        #    IDENTIDAD PURA (T1) -- no protege, no sube nada, no desbloquea
        #    nada. Es la misma categoria que los cosmeticos (D-039).
        "moneda": "REPORTCOIN",
        # ⚠ SOLO los normales. `gigantic_pokedoll_*` son estatuas de varios
        #   bloques: otro producto, y meterlos aqui doblaria la lista con cosas
        #   que se llaman igual.
        "patron": ("pokeblocks", r"^pokedoll_[a-z0-9_]+$", "peluche"),
    },
]

CABECERA = [
    "GENERADO POR tools/gen_tienda.py — NO SE EDITA A MANO.",
    "",
    "Los identificadores se comprueban contra los JARS QUE SIRVE EL MANIFIESTO,",
    "que es literalmente lo que tiene el cliente. Si uno no existe, el generador",
    "aborta en vez de publicar un hueco.",
    "",
    "LOS PRECIOS SON PROVISIONALES. Salen de unos pocos ESCALONES definidos en el",
    "generador, no articulo a articulo: el analisis de economia sera cambiar esas",
    "cifras y reejecutar.",
    "",
    "Y LOS PELUCHES SE PAGAN EN LUNACOINS porque son identidad pura (T1). Los",
    "objetos de CRIANZA no: deciden IVs y EVs, o sea progresion competitiva, y",
    "eso por moneda de pago es la linea roja de D-007 y D-014.",
]


# ------------------------------------------------------------------ los jars

def _manifiesto() -> dict:
    puntero = json.loads(urllib.request.urlopen(PUNTERO, timeout=60).read())
    return json.loads(urllib.request.urlopen(puntero["manifest"], timeout=120).read())


def jar_de(namespace: str, manifiesto: dict) -> Path:
    """Baja (o reutiliza) el jar de ese mod. Se cachea por SU HUELLA."""
    prefijo = FUENTES[namespace]
    for f in manifiesto["files"]:
        nombre = f["path"].rsplit("/", 1)[-1]
        if f["path"].startswith("mods/") and nombre.lower().startswith(prefijo.lower()):
            destino = CACHE / f"{namespace}-{f['sha1'][:10]}.jar"
            # ⚠ El nombre lleva la huella, asi que una version nueva es un
            #   fichero nuevo: no hay forma de quedarse con uno viejo en cache.
            if not destino.exists():
                CACHE.mkdir(parents=True, exist_ok=True)
                datos = urllib.request.urlopen(f["url"], timeout=300).read()
                if hashlib.sha1(datos).hexdigest() != f["sha1"]:
                    sys.exit(f"  {nombre} bajado y NO cuadra con su sha1")
                destino.write_bytes(datos)
                print(f"  bajado   {nombre}")
            return destino
    sys.exit(f"  No encuentro ningun mod que empiece por «{prefijo}» en el "
             f"manifiesto publicado")


def ruta_larga(p: Path) -> str:
    r"""
    La misma ruta, y solo si hace falta, con el prefijo de rutas largas.

    ⚠⚠ EL LIMITE DE 260 CARACTERES ES REAL Y AQUI SE CRUZA SOLO. La cache de
    Loom mete la version, el mapeo y una huella en cada carpeta, y dentro de un
    worktree --que ya cuelga de `.claude/worktrees/<nombre>/`-- el jar de
    Minecraft se pasa. Sin el prefijo, Python dice «el sistema no puede
    encontrar la ruta» sobre un fichero QUE EXISTE, que es de los errores que
    mas despistan.

    ⚠ Y SE PONE SOLO CUANDO HACE FALTA: con una ruta corta, `\?\` hace que
      `zipfile` conteste «Invalid argument». La cura no puede ser peor que la
      enfermedad.
    """
    r = str(p.resolve())
    if sys.platform == "win32" and len(r) > 240 and not r.startswith("\\?\\"):
        return "\\?\\" + r
    return r


def jar_de_vainilla() -> Path:
    """El jar de Minecraft que deja Fabric Loom al compilar el mod."""
    raices = [RAIZ / "mod" / ".gradle" / "loom-cache" / "minecraftMaven",
              Path.home() / ".gradle" / "caches" / "fabric-loom" / "minecraftMaven"]
    for raiz in raices:
        if not raiz.exists():
            continue
        # `common` y `clientOnly` valen los dos: los objetos estan en los dos.
        for j in sorted(raiz.rglob("*1.21.1*.jar")):
            if "sources" in j.name or "intermediary" in j.name:
                continue
            try:
                with zipfile.ZipFile(ruta_larga(j)) as z:
                    if "assets/minecraft/lang/en_us.json" in z.namelist():
                        return j
            except (OSError, zipfile.BadZipFile):
                continue
    sys.exit("  No encuentro el jar de Minecraft 1.21.1.\n"
             "  Sale de compilar el mod al menos una vez:  mod/build.sh")


# ------------------------------------------------------------- los objetos

def items_del_jar(jar: Path, namespace: str) -> dict:
    """
    Los objetos de un jar: {id_corto: nombre en ingles}.

    ⚠ Se cruzan DOS fuentes: la clave de idioma y el modelo de objeto. Solo con
    la primera entrarian bloques y entidades; solo con la segunda, cosas sin
    nombre. Un objeto que se pueda tener en la mano tiene las dos.

    ⚠⚠ Y LA CLAVE PUEDE SER `item.` O `block.`: un mod de muebles registra
       BLOQUES, y su objeto se llama `block.cobblefurnies.oak_chair`. Mirando
       solo `item.` salian CERO objetos de CobbleFurnies -- y no daria error,
       daria una categoria vacia.
    """
    with zipfile.ZipFile(ruta_larga(jar)) as z:
        nombres = z.namelist()
        ruta = f"assets/{namespace}/lang/en_us.json"
        if ruta not in nombres:
            sys.exit(f"  {jar.name} no trae {ruta}")
        lang = json.loads(z.read(ruta).decode("utf-8"))
        modelos = {
            n.split("/")[-1][:-5]
            for n in nombres
            if n.startswith(f"assets/{namespace}/models/item/") and n.endswith(".json")
        }
    salida = {}
    for clave, nombre in lang.items():
        for pre in (f"item.{namespace}.", f"block.{namespace}."):
            if clave.startswith(pre):
                corto = clave[len(pre):]
                if corto in modelos:
                    salida[corto] = nombre
    return salida


def catalogo_de_objetos(solo=None) -> dict:
    """{namespace: {id_corto: nombre}} de todo lo que puede vender la tienda."""
    manifiesto = None
    salida = {}
    for ns in FUENTES:
        if solo and ns != solo:
            continue
        if manifiesto is None:
            manifiesto = _manifiesto()
        salida[ns] = items_del_jar(jar_de(ns, manifiesto), ns)
    if not solo or solo == "minecraft":
        salida["minecraft"] = items_del_jar(jar_de_vainilla(), "minecraft")
    return salida


# ------------------------------------------------------------------ generar

def generar(objetos: dict) -> dict:
    faltan, repetidos, vistos = [], [], set()
    categorias = []

    def existe(id_completo: str) -> bool:
        ns, _, corto = id_completo.partition(":")
        return corto in objetos.get(ns, {})

    for cat in CATEGORIAS:
        if not existe(cat["icono"]):
            faltan.append(f"{cat['id']} (icono) -> {cat['icono']}")

        moneda = cat.get("moneda", "POKEDOLLAR")
        # Los articulos vienen como (id, escalon, etiqueta) y, si hace falta un
        # proveedor, como (id, escalon, etiqueta, quien_lo_fabrica).
        pedidos = [tuple(a) + ("",) * (4 - len(a)) for a in cat.get("articulos", [])]
        if "patron" in cat:
            ns, rx, escalon = cat["patron"]
            pat = re.compile(rx)
            hallados = sorted(k for k in objetos.get(ns, {}) if pat.match(k))
            if not hallados:
                faltan.append(f"{cat['id']} (patron {rx!r} en {ns}) -> 0 objetos")
            pedidos += [(f"{ns}:{k}", escalon, None, "") for k in hallados]

        entradas = []
        for id_completo, escalon, etiqueta, entrega in pedidos:
            if not existe(id_completo):
                faltan.append(f"{cat['id']} -> {id_completo}")
                continue

            # ⚠⚠ LA CLAVE ES (objeto, quien lo fabrica), NO SOLO EL OBJETO. Las
            #    cinco protecciones son las cinco un `minecraft:player_head`: con
            #    la clave vieja se habrian visto como cuatro repetidos y, peor,
            #    el servidor habria entregado siempre la primera.
            clave = id_completo + ("#" + entrega if entrega else "")
            # ⚠ El mismo articulo en dos categorias tendria DOS precios, y el
            #   servidor busca por (categoria, clave): el jugador veria un
            #   precio distinto segun por donde entrara.
            if clave in vistos:
                repetidos.append(clave)
            vistos.add(clave)

            # ⚠ El escalon dice ademas EN QUE SE PAGA: los de `ESCALONES_LUNA`
            #   son LunaCoins. Asi no puede haber un precio de LunaCoins con la
            #   moneda de Plata al lado, que es la clase de descuadre que nadie
            #   mira hasta que alguien compra algo por 40 de Plata.
            if escalon in ESCALONES_LUNA:
                compra, mon = ESCALONES_LUNA[escalon], "REPORTCOIN"
            elif escalon in PROTECCIONES:
                compra, mon = PROTECCIONES[escalon], "POKEDOLLAR"
            else:
                compra, mon = ESCALONES[escalon], "POKEDOLLAR"

            # ⚠⚠ UNA MONEDA NO COMERCIABLE NO PUEDE TENER RECOMPRA (D-014), y
            #    hay cosas que tampoco deben tenerla aunque se paguen en Plata:
            #    devolver un modulo al banco seria vender un terreno que sigue
            #    protegido.
            vendible = mon == "POKEDOLLAR" and cat.get("recompra", True)
            entrada = {"item": id_completo, "buy": compra,
                       "sell": max(1, int(compra * RECOMPRA)) if vendible else 0}
            if mon != moneda:
                entrada["currency"] = mon
            if etiqueta:
                entrada["label"] = etiqueta
            if entrega:
                entrada["give"] = entrega
            entradas.append(entrada)

        c = {
            "id": cat["id"],
            "name": cat["nombre"],
            "icon": cat["icono"],
            "description": cat["descripcion"],
            "entries": entradas,
        }
        if moneda != "POKEDOLLAR":
            c["currency"] = moneda
        categorias.append(c)

    if faltan:
        print("\n  NO EXISTEN EN LOS JARS PUBLICADOS:")
        for f in faltan:
            print("   ", f)
        sys.exit("\n  Abortado: el catalogo no puede prometer lo que no hay.")
    if repetidos:
        sys.exit(f"\n  Abortado: articulos en dos categorias: {repetidos}")

    return {"_comment": CABECERA, "categories": categorias}


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--listar", metavar="NAMESPACE", nargs="?", const="*",
                    help="los objetos de un mod (o de todos)")
    ap.add_argument("--buscar", metavar="TEXTO", help="buscar un objeto por nombre")
    args = ap.parse_args()

    solo = None if args.listar in (None, "*") else args.listar
    objetos = catalogo_de_objetos(solo)
    for ns, xs in sorted(objetos.items()):
        print(f"  {ns:<14} {len(xs)} objetos con modelo y nombre")

    if args.listar:
        for ns, xs in sorted(objetos.items()):
            for corto, nombre in sorted(xs.items()):
                print(f"    {ns}:{corto:<34} {nombre}")
        return
    if args.buscar:
        t = args.buscar.lower()
        for ns, xs in sorted(objetos.items()):
            for corto, nombre in sorted(xs.items()):
                if t in corto.lower() or t in nombre.lower():
                    print(f"    {ns}:{corto:<34} {nombre}")
        return

    catalogo = generar(objetos)
    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.write_text(
        json.dumps(catalogo, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    total = 0
    print()
    for c in catalogo["categories"]:
        n = len(c["entries"])
        total += n
        # ⚠ El resumen se agrupa POR MONEDA. Con la moneda de la categoria
        #   decia «500 a 150.000 de Plata» en PROTECCIONES, y ese 500 son
        #   LunaCoins: un resumen que mezcla dos monedas en un rango no dice
        #   nada, y de hecho engaña.
        porMoneda = {}
        for e in c["entries"]:
            m = e.get("currency", c.get("currency", "POKEDOLLAR"))
            porMoneda.setdefault(m, []).append(e["buy"])
        trozos = []
        for m, ps in sorted(porMoneda.items()):
            nombre = "LunaCoins" if m == "REPORTCOIN" else "Plata"
            trozos.append(f"{min(ps):,} a {max(ps):,} de {nombre}"
                          if min(ps) != max(ps) else f"{min(ps):,} de {nombre}")
        print(f"    {c['id']:12} {n:4} articulos   " + "  ·  ".join(trozos))
    print(f"\n  {len(catalogo['categories'])} categorias, {total} articulos")
    print(f"  -> {SALIDA}")
    print("\n  OJO - PRECIOS PROVISIONALES: se retocan en ESCALONES, arriba del todo.")


if __name__ == "__main__":
    main()
