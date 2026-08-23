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

SOLO COBBLEMON, Y SOLO LO IMPRESCINDIBLE
---------------------------------------
Dos decisiones del usuario del mismo dia (2026-08-23), y la segunda es la que
manda:

  1. «en la tienda solo van a ver articulos de Cobblemon, ya lo que sea de
     Minecraft ellos en la exploracion lo pueden conseguir»
  2. «items basicos, los necesarios. En Poke Ball la normal. Item de curar al
     Pokemon entre 20 % a un 50 %. Items que se requieren para craftear cosas
     basicas del Pokemon. Lo otro ellos tienen que conseguirlo explorando»

O sea que esto NO es un catalogo: son PRIMEROS AUXILIOS. Nueve articulos.

Y encaja con lo que ya estaba construido: las bayas y las bellotas son justo
lo que da XP al oficio AGRICULTOR, y la madera y la piedra lo del MINERO.
Vender lo que producen los oficios seria competir con ellos. Y una tienda
completa vacia el mundo: si todo se compra, explorar solo sirve para
conseguir dinero.

LOS PRECIOS SON PROVISIONALES, Y A PROPOSITO
--------------------------------------------
Tambien decision del usuario: «mas adelante definimos precios porque
necesitamos hacer un analisis general de la economia para que todo quede bien
equilibrado».

Por eso los precios no se escriben articulo a articulo: hay CINCO ESCALONES y
cada uno dice a cual pertenece. Aplicar el analisis sera cambiar cinco cifras.
Los anclajes salen de la configuracion real de produccion: Poke Ball 400,
Pocion 600, Superpocion 900, Revivir 3000.
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
    "basico":  200,
    "comun":   400,     # <- ancla: Poke Ball, de la config real de produccion
    "medio":   600,     # <- ancla: Pocion
    "bueno":   900,     # <- ancla: Superpocion
    "raro":  3_000,     # <- ancla: Revivir
}
RECOMPRA = 0.10          # el banco paga el 10 % . tambien provisional

# ------------------------------------------------------------- LAS CATEGORIAS
#
# ⚠⚠ LA TIENDA ES DELIBERADAMENTE DIMINUTA. Orden del usuario (2026-08-23):
#    «items basicos, los necesarios, los que necesitarian. En Poke Ball la
#    normal. Item de curar al Pokemon entre 20 % a un 50 %. Items que se
#    requieren para craftear cosas basicas del Pokemon. Lo otro ellos tienen
#    que conseguirlo explorando».
#
#    Es una tienda de PRIMEROS AUXILIOS, no un catalogo. Todo lo demas --las
#    otras 20 balls, las piedras evolutivas, los objetos de combate, las
#    vitaminas-- se consigue jugando, y eso es lo que hace que jugar valga la
#    pena. Una tienda completa vacia el mundo: si todo se compra, explorar solo
#    sirve para conseguir dinero.
#
# ⚠ CINCO CATEGORIAS COMO MAXIMO (la pantalla las dibuja en una lista vertical
#   que acaba en 762; la sexta caeria fuera del marco). Hoy son dos.
CATEGORIAS = [
    {
        "id": "esencial",
        "nombre": "§cLo esencial",
        "icono": "poke_ball",
        "descripcion": "La Poké Ball normal y poco más. Lo demás se encuentra.",
        "articulos": [
            # ⚠ SOLO LA NORMAL. Ni Great, ni Ultra, ni las de bellota: esas se
            #   craftean con bellotas --que es justo lo que da XP al oficio
            #   AGRICULTOR-- o se encuentran. Venderlas competiria con jugar.
            ("poke_ball", "comun", None),
            # ⚠ ESTO NO ES UN OBJETO DE COMBATE, ES UN MATERIAL. La MAQUINA
            #   CURATIVA se craftea con cobre, hierro, redstone y UN MAX REVIVE
            #   (verificado en su receta), y el Max Revive NO se craftea: sale
            #   de cofres. Sin el, montarse la base es cuestion de suerte.
            #   Es exactamente el caso que pedia el usuario: «items que se
            #   requieren para craftear cosas basicas del Pokemon».
            ("max_revive", "raro", "§fMáx. Revivir §8· para la Máquina Curativa"),
        ],
    },
    {
        "id": "cuidado",
        "nombre": "§aCuidado",
        "icono": "potion",
        "descripcion": "Curar un poco y quitar estados. Lo del día a día.",
        "articulos": [
            # ⚠ LOS DOS QUE CAEN EN LA BANDA QUE PIDIO EL USUARIO (20-50 %), y
            #   los numeros salen de SUS DATOS, no de memoria:
            #   data/cobblemon/mechanics/potions.json dice 20 / 60 / 120 PS.
            #   Sobre un Pokemon de nivel medio (80-130 PS) eso es ~20 % y ~50 %.
            #   La HIPERPOCION (120) cura una barra entera de casi cualquiera:
            #   por eso NO esta, y esa es la linea.
            ("potion", "medio", "§fPoción §8· cura 20 PS"),
            ("super_potion", "bueno", "§fSuperpoción §8· cura 60 PS"),
            ("antidote", "basico", None),
            ("burn_heal", "basico", None),
            ("ice_heal", "basico", None),
            ("paralyze_heal", "basico", None),
            ("awakening", "basico", None),
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
