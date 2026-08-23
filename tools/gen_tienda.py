"""Genera el catalogo de la tienda A PARTIR DEL JAR DE COBBLEMON.

    python tools/gen_tienda.py
    python tools/gen_tienda.py --listar        que items tiene Cobblemon
    python tools/gen_tienda.py --buscar stone   buscar por nombre

POR QUE SE GENERA Y NO SE ESCRIBE
---------------------------------
Es la misma leccion que costo 62 cosmeticos que no existian: un catalogo
escrito a mano PROMETE cosas, y nadie comprueba que esten. `ShopCatalog.load()`
se salta los objetos que no existan con un aviso en el log --que nadie mira--,
asi que un identificador mal escrito no da error: da una tienda con un hueco.

Generarlo del jar no puede prometer lo que no hay. Si un identificador no
existe, ESTE SCRIPT ABORTA y no se publica nada.

SOLO COBBLEMON
--------------
Decision del usuario (2026-08-23): «en la tienda solo van a ver articulos de
Cobblemon, ya lo que sea de Minecraft ellos en la exploracion lo pueden
conseguir».

Y encaja con lo que ya habia: las bayas y las bellotas NO se venden porque
son justo lo que da XP al oficio AGRICULTOR, y la madera y la piedra son lo
del MINERO. Vender lo que los oficios producen seria competir con ellos.

LOS PRECIOS SON PROVISIONALES, Y A PROPOSITO
--------------------------------------------
Tambien decision del usuario: «mas adelante definimos precios porque
necesitamos hacer un analisis general de la economia para que todo quede bien
equilibrado».

Por eso NO hay 80 precios escritos a mano: hay CINCO ESCALONES y cada articulo
dice a cual pertenece. Retocar la economia entera es cambiar los cinco numeros
de ESCALONES, no revisar ochenta lineas. Los cuatro anclajes que vienen de la
configuracion real de produccion (Poke Ball 400, Pocion 600, Superpocion 900,
Revivir 3000) se conservan como referencia de los escalones.
"""

import argparse
import json
import sys
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
JAR = RAIZ / "build" / "cobblemon-para-texturas.jar"
SALIDA = RAIZ / "mod" / "src" / "main" / "resources" / "data" / "lunaeternal" / "shop_catalog.json"

# ---------------------------------------------------------------- LOS PRECIOS
#
# ⚠ PROVISIONALES. Ver el docstring. Se retocan AQUI y en ningun otro sitio.
#
# La venta al banco es un PORCENTAJE del precio de compra, no un numero suelto:
# asi el invariante de no-arbitraje (vender por mas de lo que cuesta = dinero
# infinito) no puede romperse por un despiste al teclear.
ESCALONES = {
    "basico":    200,
    "comun":     400,     # <- ancla: Poke Ball, de la config de produccion
    "bueno":     900,     # <- ancla: Superpocion
    "raro":      3_000,   # <- ancla: Revivir
    "muy_raro": 10_000,
}
RECOMPRA = 0.10          # el banco paga el 10 % . tambien provisional

# ------------------------------------------------------------- LAS CATEGORIAS
#
# ⚠ CINCO COMO MAXIMO. No es una preferencia: la pantalla las dibuja en una
#   lista vertical de tarjetas de 94 px en un panel que acaba en 762, y la
#   SEXTA se dibujaria fuera del marco -- invisible e impulsable. El autotest
#   lo comprueba (`las categorias caben en el panel`).
#
# ⚠ Y DIECIOCHO ARTICULOS COMO MAXIMO por categoria: caben 6 por pagina y el
#   autotest no deja pasar de 3 paginas. Una lista mas larga se recorre con
#   flechas que nadie pulsa hasta el final.
CATEGORIAS = [
    {
        "id": "captura",
        "nombre": "§cCaptura",
        "icono": "poke_ball",
        "descripcion": "Balls, caña y cebo. Lo que hace falta para atrapar.",
        "articulos": [
            ("poke_ball", "comun", None),
            ("great_ball", "comun", None),
            ("ultra_ball", "bueno", None),
            ("premier_ball", "comun", None),
            ("heal_ball", "bueno", None),
            ("net_ball", "bueno", None),
            ("nest_ball", "bueno", None),
            ("dive_ball", "bueno", None),
            ("dusk_ball", "bueno", None),
            ("timer_ball", "bueno", None),
            ("quick_ball", "bueno", None),
            ("repeat_ball", "bueno", None),
            ("luxury_ball", "bueno", None),
            ("level_ball", "bueno", None),
            ("lure_ball", "bueno", None),
            # ⚠ LA BALL DEL SERVIDOR. Su efectividad sube con la luna llena, y
            #   eso es NATIVO de Cobblemon, no un invento nuestro. Recompensa
            #   saber CUANDO salir, que es el pilar de la vision -- por eso
            #   lleva etiqueta propia y escalon alto: se usa la noche buena, no
            #   todas.
            ("moon_ball", "raro", "§bMoon Ball §8· mejor con luna llena"),
            ("poke_rod", "bueno", "§ePoké Caña §8· para el oficio de PESCADOR"),
            ("poke_bait", "basico", None),
        ],
    },
    {
        "id": "medicina",
        "nombre": "§aMedicina",
        "icono": "potion",
        "descripcion": "Curar, revivir y quitar estados. Lo que salva un combate.",
        "articulos": [
            ("potion", "comun", None),
            ("super_potion", "bueno", None),
            ("hyper_potion", "bueno", None),
            ("max_potion", "raro", None),
            ("full_restore", "raro", None),
            ("revive", "raro", None),
            ("max_revive", "muy_raro", None),
            ("full_heal", "bueno", None),
            ("antidote", "basico", None),
            ("burn_heal", "basico", None),
            ("ice_heal", "basico", None),
            ("paralyze_heal", "basico", None),
            ("awakening", "basico", None),
            ("ether", "bueno", None),
            ("max_ether", "raro", None),
            ("elixir", "bueno", None),
            ("max_elixir", "raro", None),
            ("revival_herb", "bueno", None),
        ],
    },
    {
        "id": "evolucion",
        "nombre": "§dEvolución",
        "icono": "fire_stone",
        "descripcion": "Piedras y objetos que hacen evolucionar.",
        "articulos": [
            ("fire_stone", "raro", None),
            ("water_stone", "raro", None),
            ("thunder_stone", "raro", None),
            ("leaf_stone", "raro", None),
            ("moon_stone", "raro", None),
            ("sun_stone", "raro", None),
            ("shiny_stone", "raro", None),
            ("dusk_stone", "raro", None),
            ("dawn_stone", "raro", None),
            ("ice_stone", "raro", None),
            ("link_cable", "raro", None),
            ("metal_coat", "raro", None),
            ("kings_rock", "raro", None),
            ("dragon_scale", "raro", None),
            ("upgrade", "raro", None),
            ("dubious_disc", "raro", None),
            ("razor_claw", "raro", None),
            ("razor_fang", "raro", None),
        ],
    },
    {
        "id": "combate",
        "nombre": "§6Combate",
        "icono": "choice_band",
        "descripcion": "Objetos equipados. Cambian cómo pelea tu equipo.",
        "articulos": [
            ("leftovers", "muy_raro", None),
            ("focus_sash", "raro", None),
            ("focus_band", "bueno", None),
            ("choice_band", "muy_raro", None),
            ("choice_scarf", "muy_raro", None),
            ("choice_specs", "muy_raro", None),
            ("life_orb", "muy_raro", None),
            ("assault_vest", "muy_raro", None),
            ("eviolite", "raro", None),
            ("rocky_helmet", "raro", None),
            ("black_sludge", "bueno", None),
            ("expert_belt", "raro", None),
            ("muscle_band", "raro", None),
            ("wise_glasses", "raro", None),
            ("scope_lens", "raro", None),
            ("quick_claw", "raro", None),
            ("air_balloon", "raro", None),
            ("heavy_duty_boots", "raro", None),
        ],
    },
    {
        "id": "entrenamiento",
        "nombre": "§bEntrenamiento",
        "icono": "rare_candy",
        "descripcion": "Vitaminas y caramelos. Suben a tu equipo, no a ti.",
        "articulos": [
            ("hp_up", "raro", None),
            ("protein", "raro", None),
            ("iron", "raro", None),
            ("calcium", "raro", None),
            ("zinc", "raro", None),
            ("carbos", "raro", None),
            ("pp_up", "raro", None),
            ("pp_max", "muy_raro", None),
            ("exp_share", "raro", None),
            ("lucky_egg", "muy_raro", None),
            ("exp_candy_xs", "basico", None),
            ("exp_candy_s", "comun", None),
            ("exp_candy_m", "bueno", None),
            ("exp_candy_l", "raro", None),
            ("exp_candy_xl", "muy_raro", None),
            ("rare_candy", "muy_raro", None),
            ("ability_capsule", "muy_raro", None),
            ("ability_patch", "muy_raro", None),
        ],
    },
]

CABECERA = [
    "GENERADO POR tools/gen_tienda.py — NO SE EDITA A MANO.",
    "",
    "SOLO ARTICULOS DE COBBLEMON (decision del usuario, 2026-08-23). Lo de",
    "Minecraft se consigue explorando, y las bayas y bellotas son justo lo que",
    "da XP al oficio AGRICULTOR: venderlas competiria con el.",
    "",
    "LOS PRECIOS SON PROVISIONALES. Salen de CINCO escalones definidos en el",
    "generador, no de ochenta numeros escritos a mano, precisamente para que el",
    "analisis de economia que falta sea cambiar cinco cifras. Los anclajes",
    "vienen de la configuracion real de produccion: Poke Ball 400, Pocion 600,",
    "Superpocion 900, Revivir 3000.",
    "",
    "INVARIANTE: ningun objeto se vende al banco por mas de lo que cuesta.",
    "El arranque lo comprueba y el autotest tambien. La recompra es un",
    "PORCENTAJE del precio, no un numero suelto, para que no pueda romperse",
    "por un despiste al teclear.",
]


def items_de_cobblemon() -> dict:
    """Los objetos que Cobblemon registra de verdad, con su nombre en ingles.

    ⚠ Se cruzan DOS fuentes: la clave de idioma (`item.cobblemon.X`) y el
    modelo de objeto (`models/item/X.json`). Solo con la primera entrarian
    bloques y entidades; solo con la segunda, cosas sin nombre. Un objeto que
    se pueda tener en la mano tiene las dos.
    """
    if not JAR.exists():
        sys.exit(f"No encuentro el jar de Cobblemon en {JAR}")
    with zipfile.ZipFile(JAR) as z:
        lang = json.loads(z.read("assets/cobblemon/lang/en_us.json").decode("utf-8"))
        modelos = {
            n.split("/")[-1][:-5]
            for n in z.namelist()
            if n.startswith("assets/cobblemon/models/item/") and n.endswith(".json")
        }
    salida = {}
    for clave, nombre in lang.items():
        if not clave.startswith("item.cobblemon."):
            continue
        corto = clave[len("item.cobblemon."):]
        if corto in modelos:
            salida[corto] = nombre
    return salida


def generar(existentes: dict) -> dict:
    faltan = []
    repetidos = []
    vistos = set()
    categorias = []

    for cat in CATEGORIAS:
        if cat["icono"] not in existentes:
            faltan.append(f"{cat['id']} (icono) -> {cat['icono']}")
        entradas = []
        for corto, escalon, etiqueta in cat["articulos"]:
            if corto not in existentes:
                faltan.append(f"{cat['id']} -> {corto}")
                continue
            # ⚠ El mismo objeto en dos categorias tendria DOS precios, y el
            #   servidor busca por (categoria, objeto): el jugador veria un
            #   precio distinto segun por donde entrara.
            if corto in vistos:
                repetidos.append(corto)
            vistos.add(corto)

            compra = ESCALONES[escalon]
            venta = max(1, int(compra * RECOMPRA))
            entrada = {"item": f"cobblemon:{corto}", "buy": compra, "sell": venta}
            if etiqueta:
                entrada["label"] = etiqueta
            entradas.append(entrada)

        categorias.append({
            "id": cat["id"],
            "name": cat["nombre"],
            "icon": f"cobblemon:{cat['icono']}",
            "description": cat["descripcion"],
            "entries": entradas,
        })

    if faltan:
        print("\n  NO EXISTEN EN COBBLEMON 1.7.3:")
        for f in faltan:
            print("   ", f)
        sys.exit("\n  Abortado: el catalogo no puede prometer lo que no hay.")
    if repetidos:
        sys.exit(f"\n  Abortado: articulos en dos categorias: {repetidos}")

    return {"_comment": CABECERA, "categories": categorias}


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--listar", action="store_true", help="todos los items de Cobblemon")
    ap.add_argument("--buscar", metavar="TEXTO", help="buscar un item por nombre")
    args = ap.parse_args()

    existentes = items_de_cobblemon()
    print(f"  Cobblemon 1.7.3: {len(existentes)} objetos con modelo y nombre")

    if args.listar:
        for corto, nombre in sorted(existentes.items()):
            print(f"    {corto:32} {nombre}")
        return
    if args.buscar:
        t = args.buscar.lower()
        for corto, nombre in sorted(existentes.items()):
            if t in corto.lower() or t in nombre.lower():
                print(f"    {corto:32} {nombre}")
        return

    catalogo = generar(existentes)
    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.write_text(
        json.dumps(catalogo, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    total = 0
    print()
    for c in catalogo["categories"]:
        n = len(c["entries"])
        total += n
        precios = [e["buy"] for e in c["entries"]]
        print(f"    {c['id']:14} {n:2} articulos   "
              f"{min(precios):,} a {max(precios):,} de Plata")
    print(f"\n  {len(catalogo['categories'])} categorias, {total} articulos, "
          f"0 de Minecraft")
    print(f"  -> {SALIDA}")
    print("\n  OJO - PRECIOS PROVISIONALES: se retocan en ESCALONES, arriba del todo.")


if __name__ == "__main__":
    main()
