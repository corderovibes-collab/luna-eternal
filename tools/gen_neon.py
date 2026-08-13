#!/usr/bin/env python3
"""
Genera TODO el contenido del mod de bloques neon (`neon/`): texturas, modelos,
blockstates, loot tables, tags, idiomas y la tabla de colores en Java.

QUE ES ESTO, EN UNA FRASE

La ciudadela es de noche permanente y va llena de neon (D-029). Eso son 16
colores x 6 formas = 96 bloques, y cada bloque necesita entre 3 y 5 ficheros
JSON. Escribirlos a mano son ~600 ficheros y un error tipografico invisible
hasta que un constructor ve un cubo negro y morado en mitad de la plaza.

    UNA SOLA FUENTE DE VERDAD:  la lista PALETA de este fichero.

De ahi sale todo, incluido `Paleta.java`. El mod NO tiene la lista de colores
escrita en Java: la lee de aqui. Asi es imposible que un color exista en el
codigo y no en las texturas, que es exactamente el fallo que produce el cubo
negro y morado.

POR QUE LOS BLOQUES BRILLAN AUNQUE NO ILUMINEN

Cada bloque lleva DOS cosas distintas que la gente confunde:

    luminance        cuanta luz SUELTA al mundo   -> propiedad `luz` (0/7/15)
    emissiveLighting como se DIBUJA a si mismo    -> siempre a tope

Es el truco del bloque de magma de vanilla. Un cartel de neon con `luz=0`
brilla igual de fuerte pero no ilumina la calle, que es justo lo que hace falta
para que una ciudad nocturna siga siendo nocturna con mil neones encendidos.

Uso:
    python tools/gen_neon.py             # regenera neon/src/main/resources
    python tools/gen_neon.py --verificar # cruza bloques, modelos y texturas
    python tools/gen_neon.py --listar    # solo imprime los IDs que generaria
"""
import argparse
import json
import shutil
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
MOD = RAIZ / "neon"
RES = MOD / "src" / "main" / "resources"
JAVA = MOD / "src" / "main" / "java" / "net" / "pokereport" / "neon"

NS = "lunaneon"

# ---------------------------------------------------------------------------
# LA PALETA
#
# Son los 16 tintes de vanilla POR NOMBRE (blanco, cian, magenta...) pero con
# los valores subidos a intensidad de neon: mas saturados y mas claros que la
# lana o el hormigon del mismo nombre. Se conservan los nombres de vanilla a
# proposito — un constructor ya sabe donde esta el "cian" sin mirar una tabla.
#
# `negro` no es un neon: es el grafito con el que se enmarca todo lo demas.
# Un neon necesita algo oscuro al lado o no se lee como neon.
# ---------------------------------------------------------------------------
PALETA = [
    # id            es              en             color      MapColor
    ("blanco",     "Blanco",       "White",       "#F2FAFF", "WHITE"),
    ("gris_claro", "Gris claro",   "Light Gray",  "#B7C4D2", "LIGHT_GRAY"),
    ("gris",       "Gris",         "Gray",        "#68788A", "GRAY"),
    ("negro",      "Negro",        "Black",       "#1B1E26", "BLACK"),
    ("marron",     "Marron",       "Brown",       "#B06A2E", "BROWN"),
    ("rojo",       "Rojo",         "Red",         "#FF2A3C", "RED"),
    ("naranja",    "Naranja",      "Orange",      "#FF7A14", "ORANGE"),
    ("amarillo",   "Amarillo",     "Yellow",      "#FFE12E", "YELLOW"),
    ("lima",       "Lima",         "Lime",        "#A6FF29", "LIME"),
    ("verde",      "Verde",        "Green",       "#1EDF5C", "GREEN"),
    ("cian",       "Cian",         "Cyan",        "#16F2E6", "CYAN"),
    ("azul_claro", "Azul claro",   "Light Blue",  "#3FBBFF", "LIGHT_BLUE"),
    ("azul",       "Azul",         "Blue",        "#2A5CFF", "BLUE"),
    ("morado",     "Morado",       "Purple",      "#9A34FF", "PURPLE"),
    ("magenta",    "Magenta",      "Magenta",     "#FF34DF", "MAGENTA"),
    ("rosa",       "Rosa",         "Pink",        "#FF8FC6", "PINK"),
]

# Las 6 formas. `sufijo` vacio = el bloque entero.
#
#   bloque    muro y suelo
#   losa      medio bloque
#   escalera  esquinas y rampas
#   pilar     columna con carcasa oscura y una linea de luz
#   panel     chapa de 1 px pegada a cualquiera de las 6 caras: el cartel
#   tubo      barra de 4x4: el tubo de neon de toda la vida
FORMAS = [
    ("",          "{c}",                 "{c} Neon"),
    ("_losa",     "Losa de neon {m}",    "{c} Neon Slab"),
    ("_escalera", "Escalera de neon {m}", "{c} Neon Stairs"),
    ("_pilar",    "Pilar de neon {m}",   "{c} Neon Pillar"),
    ("_panel",    "Panel de neon {m}",   "{c} Neon Panel"),
    ("_tubo",     "Tubo de neon {m}",    "{c} Neon Tube"),
]

# El grafito de la carcasa del pilar. No es negro puro: el negro puro contra un
# neon a tope produce un borde que vibra.
CARCASA = (22, 24, 30)

# El cielo de la ciudadela es noche fija, asi que el jugador ve los bloques
# contra un fondo oscuro. Se dibujan con el nucleo mas claro que el borde para
# que lean como "esto emite", no como "esto esta pintado".


def rgb(hexa: str) -> tuple:
    hexa = hexa.lstrip("#")
    return tuple(int(hexa[i:i + 2], 16) for i in (0, 2, 4))


def mezcla(a: tuple, b: tuple, t: float) -> tuple:
    """Interpola dos colores. t=0 devuelve a, t=1 devuelve b."""
    t = max(0.0, min(1.0, t))
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


# ---------------------------------------------------------------------------
# TEXTURAS
# ---------------------------------------------------------------------------

BLANCO = (255, 255, 255)


def tex_base(c: tuple) -> Image.Image:
    """La chapa de neon: marco oscuro, filo brillante y nucleo caliente.

    El marco es lo que hace que una pared de 40 bloques no sea una mancha
    plana: al repetirse dibuja una reticula de paneles.
    """
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    marco = mezcla(c, (0, 0, 0), 0.62)
    for y in range(16):
        for x in range(16):
            m = min(x, y, 15 - x, 15 - y)
            if m == 0:
                col = marco
            elif m == 1:
                col = mezcla(c, (0, 0, 0), 0.18)
            else:
                col = mezcla(c, BLANCO, 0.06 + 0.20 * ((m - 2) / 5.0))
            px[x, y] = (*col, 255)
    return img


def tex_pilar(c: tuple) -> Image.Image:
    """Cara lateral del pilar: carcasa oscura con una linea de luz vertical."""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    nucleo = mezcla(c, BLANCO, 0.30)
    for x in range(16):
        d = abs(x - 7.5)
        if d <= 1.5:
            i = 1.0
        elif d <= 2.5:
            i = 0.45
        elif d <= 3.5:
            i = 0.15
        else:
            i = 0.0
        col = mezcla(CARCASA, nucleo, i)
        for y in range(16):
            # Anillos de remate arriba y abajo: al apilar pilares se ve la junta.
            fin = mezcla(col, (0, 0, 0), 0.45) if y in (0, 15) else col
            px[x, y] = (*fin, 255)
    return img


def tex_pilar_tapa(c: tuple) -> Image.Image:
    """Cara superior/inferior del pilar: el ojo de luz."""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    nucleo = mezcla(c, BLANCO, 0.30)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d <= 1.5:
                i = 1.0
            elif d <= 2.5:
                i = 0.45
            elif d <= 3.5:
                i = 0.15
            else:
                i = 0.0
            col = mezcla(CARCASA, nucleo, i)
            if min(x, y, 15 - x, 15 - y) == 0:
                col = mezcla(col, (0, 0, 0), 0.45)
            px[x, y] = (*col, 255)
    return img


def tex_tubo(c: tuple) -> Image.Image:
    """El tubo: nucleo casi blanco que cae hacia el color por los lados."""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    nucleo = mezcla(c, BLANCO, 0.58)
    borde = mezcla(c, (0, 0, 0), 0.22)
    for x in range(16):
        d = abs(x - 7.5) / 7.5
        col = mezcla(nucleo, borde, d ** 0.8)
        for y in range(16):
            px[x, y] = (*col, 255)
    return img


# ---------------------------------------------------------------------------
# MODELOS
# ---------------------------------------------------------------------------

def modelo(parent: str, texturas: dict) -> dict:
    # ambientocclusion=false a proposito: la oclusion ambiental mete sombra en
    # las esquinas, y una esquina con sombra en un bloque que se dibuja a
    # brillo maximo se ve como un parche sucio.
    return {"parent": parent, "ambientocclusion": False, "textures": texturas}


# Chapa de 1 px. `facing` es hacia donde MIRA la cara visible, asi que el
# volumen se pega al lado CONTRARIO del bloque.
CAJA_PANEL = {
    "up":    ([0, 0, 0],   [16, 1, 16],  "down"),
    "down":  ([0, 15, 0],  [16, 16, 16], "up"),
    "north": ([0, 0, 15],  [16, 16, 16], "south"),
    "south": ([0, 0, 0],   [16, 16, 1],  "north"),
    "west":  ([15, 0, 0],  [16, 16, 16], "east"),
    "east":  ([0, 0, 0],   [1, 16, 16],  "west"),
}

# Barra de 4x4 centrada en cada eje.
CAJA_TUBO = {
    "y": ([6, 0, 6], [10, 16, 10], ("down", "up")),
    "x": ([0, 6, 6], [16, 10, 10], ("west", "east")),
    "z": ([6, 6, 0], [10, 10, 16], ("north", "south")),
}

CARAS = ("down", "up", "north", "south", "west", "east")


def modelo_elemento(textura: str, desde: list, hasta: list, cull: tuple) -> dict:
    """Modelo de una sola caja.

    Las UV se omiten a proposito: Minecraft las deduce de la posicion de la
    caja. Calcularlas a mano aqui son 24 numeros por modelo y seis formas de
    equivocarse, para acabar en lo mismo.
    """
    caras = {}
    for cara in CARAS:
        f = {"texture": "#neon"}
        if cara in cull:
            f["cullface"] = cara
        caras[cara] = f
    return {
        "ambientocclusion": False,
        "textures": {"particle": textura, "neon": textura},
        "elements": [{"from": desde, "to": hasta, "faces": caras}],
    }


# Tabla de variantes de escalera, copiada TAL CUAL de
# assets/minecraft/blockstates/oak_stairs.json del jar 1.21.1. Son 40 combina-
# ciones de facing/half/shape con sus rotaciones; deducirlas a mano es la clase
# de tarea que sale mal en dos de las cuarenta y no te enteras hasta que alguien
# pone una escalera mirando al revés en una esquina.
#   clave -> (variante_de_modelo, x, y)   x/y = None si no lleva rotacion
ESCALERAS = {
    ("east",  "bottom", "straight"):    ("", None, None),
    ("west",  "bottom", "straight"):    ("", None, 180),
    ("south", "bottom", "straight"):    ("", None, 90),
    ("north", "bottom", "straight"):    ("", None, 270),
    ("east",  "top",    "straight"):    ("", 180, None),
    ("west",  "top",    "straight"):    ("", 180, 180),
    ("south", "top",    "straight"):    ("", 180, 90),
    ("north", "top",    "straight"):    ("", 180, 270),
    ("east",  "bottom", "outer_right"): ("_exterior", None, None),
    ("west",  "bottom", "outer_right"): ("_exterior", None, 180),
    ("south", "bottom", "outer_right"): ("_exterior", None, 90),
    ("north", "bottom", "outer_right"): ("_exterior", None, 270),
    ("east",  "bottom", "outer_left"):  ("_exterior", None, 270),
    ("west",  "bottom", "outer_left"):  ("_exterior", None, 90),
    ("south", "bottom", "outer_left"):  ("_exterior", None, None),
    ("north", "bottom", "outer_left"):  ("_exterior", None, 180),
    ("east",  "top",    "outer_right"): ("_exterior", 180, 90),
    ("west",  "top",    "outer_right"): ("_exterior", 180, 270),
    ("south", "top",    "outer_right"): ("_exterior", 180, 180),
    ("north", "top",    "outer_right"): ("_exterior", 180, None),
    ("east",  "top",    "outer_left"):  ("_exterior", 180, None),
    ("west",  "top",    "outer_left"):  ("_exterior", 180, 180),
    ("south", "top",    "outer_left"):  ("_exterior", 180, 90),
    ("north", "top",    "outer_left"):  ("_exterior", 180, 270),
    ("east",  "bottom", "inner_right"): ("_interior", None, None),
    ("west",  "bottom", "inner_right"): ("_interior", None, 180),
    ("south", "bottom", "inner_right"): ("_interior", None, 90),
    ("north", "bottom", "inner_right"): ("_interior", None, 270),
    ("east",  "bottom", "inner_left"):  ("_interior", None, 270),
    ("west",  "bottom", "inner_left"):  ("_interior", None, 90),
    ("south", "bottom", "inner_left"):  ("_interior", None, None),
    ("north", "bottom", "inner_left"):  ("_interior", None, 180),
    ("east",  "top",    "inner_right"): ("_interior", 180, 90),
    ("west",  "top",    "inner_right"): ("_interior", 180, 270),
    ("south", "top",    "inner_right"): ("_interior", 180, 180),
    ("north", "top",    "inner_right"): ("_interior", 180, None),
    ("east",  "top",    "inner_left"):  ("_interior", 180, None),
    ("west",  "top",    "inner_left"):  ("_interior", 180, 180),
    ("south", "top",    "inner_left"):  ("_interior", 180, 90),
    ("north", "top",    "inner_left"):  ("_interior", 180, 270),
}

# Los tres niveles de la propiedad `luz`. El indice es el valor del blockstate;
# el numero es la luz que suelta. Tiene que coincidir con NIVELES de Neon.java,
# y por eso Neon.java se genera desde aqui.
NIVELES = [0, 7, 15]


def escribir(ruta: Path, datos) -> None:
    ruta.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(datos, Image.Image):
        datos.save(ruta)
    else:
        ruta.write_text(json.dumps(datos, indent=2, ensure_ascii=False) + "\n",
                        encoding="utf-8")


def con_luz(variantes: dict) -> dict:
    """Multiplica un mapa de variantes por los tres valores de `luz`.

    Los tres apuntan al MISMO modelo: `luz` cambia cuanta luz suelta el bloque,
    no como se ve. Minecraft no permite omitir una propiedad de unas variantes
    si aparece en otras, asi que hay que enumerarlas.
    """
    salida = {}
    for clave, valor in variantes.items():
        for nivel in range(len(NIVELES)):
            partes = [p for p in (clave, f"luz={nivel}") if p]
            salida[",".join(partes)] = valor
    return salida


def generar_color(cid: str, hexa: str, escritos: list) -> None:
    c = rgb(hexa)
    base = f"neon_{cid}"
    tex = f"{NS}:block/{base}"

    # --- texturas ---------------------------------------------------------
    escribir(RES / f"assets/{NS}/textures/block/{base}.png", tex_base(c))
    escribir(RES / f"assets/{NS}/textures/block/{base}_pilar.png", tex_pilar(c))
    escribir(RES / f"assets/{NS}/textures/block/{base}_pilar_tapa.png", tex_pilar_tapa(c))
    escribir(RES / f"assets/{NS}/textures/block/{base}_tubo.png", tex_tubo(c))

    def mod(nombre: str, datos: dict) -> None:
        escribir(RES / f"assets/{NS}/models/block/{nombre}.json", datos)

    def item(nombre: str, padre: str) -> None:
        escribir(RES / f"assets/{NS}/models/item/{nombre}.json", {"parent": padre})

    def estado(nombre: str, variantes: dict) -> None:
        escribir(RES / f"assets/{NS}/blockstates/{nombre}.json",
                 {"variants": con_luz(variantes)})

    def botin(nombre: str, doble: bool = False) -> None:
        """Loot table. Sin ella el bloque se rompe y no suelta nada."""
        funciones = [{"function": "minecraft:explosion_decay"}]
        if doble:
            funciones.insert(0, {
                "function": "minecraft:set_count", "add": False, "count": 2.0,
                "conditions": [{
                    "condition": "minecraft:block_state_property",
                    "block": f"{NS}:{nombre}",
                    "properties": {"type": "double"},
                }],
            })
        escribir(RES / f"data/{NS}/loot_table/blocks/{nombre}.json", {
            "type": "minecraft:block",
            "random_sequence": f"{NS}:blocks/{nombre}",
            "pools": [{
                "rolls": 1.0, "bonus_rolls": 0.0,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{nombre}",
                             "functions": funciones}],
            }],
        })

    # --- bloque entero ----------------------------------------------------
    mod(base, modelo("minecraft:block/cube_all", {"all": tex}))
    item(base, f"{NS}:block/{base}")
    estado(base, {"": {"model": f"{NS}:block/{base}"}})
    botin(base)

    # --- losa -------------------------------------------------------------
    caras_losa = {"bottom": tex, "top": tex, "side": tex}
    mod(f"{base}_losa", modelo("minecraft:block/slab", caras_losa))
    mod(f"{base}_losa_arriba", modelo("minecraft:block/slab_top", caras_losa))
    item(f"{base}_losa", f"{NS}:block/{base}_losa")
    estado(f"{base}_losa", {
        "type=bottom": {"model": f"{NS}:block/{base}_losa"},
        "type=top":    {"model": f"{NS}:block/{base}_losa_arriba"},
        "type=double": {"model": f"{NS}:block/{base}"},
    })
    botin(f"{base}_losa", doble=True)

    # --- escalera ---------------------------------------------------------
    for sufijo, padre in (("", "stairs"), ("_interior", "inner_stairs"),
                          ("_exterior", "outer_stairs")):
        mod(f"{base}_escalera{sufijo}",
            modelo(f"minecraft:block/{padre}", caras_losa))
    item(f"{base}_escalera", f"{NS}:block/{base}_escalera")
    variantes = {}
    for (facing, half, shape), (sufijo, x, y) in ESCALERAS.items():
        v = {"model": f"{NS}:block/{base}_escalera{sufijo}"}
        if x is not None:
            v["x"] = x
        if y is not None:
            v["y"] = y
        if x is not None or y is not None:
            v["uvlock"] = True
        variantes[f"facing={facing},half={half},shape={shape}"] = v
    estado(f"{base}_escalera", variantes)
    botin(f"{base}_escalera")

    # --- pilar ------------------------------------------------------------
    caras_pilar = {"end": f"{tex}_pilar_tapa", "side": f"{tex}_pilar"}
    mod(f"{base}_pilar", modelo("minecraft:block/cube_column", caras_pilar))
    mod(f"{base}_pilar_h",
        modelo("minecraft:block/cube_column_horizontal", caras_pilar))
    item(f"{base}_pilar", f"{NS}:block/{base}_pilar")
    estado(f"{base}_pilar", {
        "axis=y": {"model": f"{NS}:block/{base}_pilar"},
        "axis=z": {"model": f"{NS}:block/{base}_pilar_h", "x": 90},
        "axis=x": {"model": f"{NS}:block/{base}_pilar_h", "x": 90, "y": 90},
    })
    botin(f"{base}_pilar")

    # --- panel ------------------------------------------------------------
    variantes = {}
    for cara, (desde, hasta, cull) in CAJA_PANEL.items():
        mod(f"{base}_panel_{cara}", modelo_elemento(tex, desde, hasta, (cull,)))
        variantes[f"facing={cara}"] = {"model": f"{NS}:block/{base}_panel_{cara}"}
    item(f"{base}_panel", f"{NS}:block/{base}_panel_up")
    estado(f"{base}_panel", variantes)
    botin(f"{base}_panel")

    # --- tubo -------------------------------------------------------------
    variantes = {}
    for eje, (desde, hasta, cull) in CAJA_TUBO.items():
        mod(f"{base}_tubo_{eje}",
            modelo_elemento(f"{tex}_tubo", desde, hasta, cull))
        variantes[f"axis={eje}"] = {"model": f"{NS}:block/{base}_tubo_{eje}"}
    item(f"{base}_tubo", f"{NS}:block/{base}_tubo_y")
    estado(f"{base}_tubo", variantes)
    botin(f"{base}_tubo")

    escritos.extend(f"{base}{sufijo}" for sufijo, _, _ in FORMAS)


def generar_idiomas() -> None:
    es, en = {}, {}
    for cid, nombre_es, nombre_en, _, _ in PALETA:
        for sufijo, plantilla_es, plantilla_en in FORMAS:
            clave = f"block.{NS}.neon_{cid}{sufijo}"
            # "Neon cian" pero "Losa de neon cian": la primera lleva mayuscula
            # al principio y el resto no.
            es[clave] = plantilla_es.format(c=f"Neon {nombre_es.lower()}",
                                            m=nombre_es.lower())
            en[clave] = plantilla_en.format(c=nombre_en)
    es[f"itemGroup.{NS}.neon"] = "Luna Eternal · Neon"
    en[f"itemGroup.{NS}.neon"] = "Luna Eternal · Neon"
    escribir(RES / f"assets/{NS}/lang/es_es.json", es)
    escribir(RES / f"assets/{NS}/lang/en_us.json", en)


def generar_tags(ids: list) -> None:
    """Que el pico sea la herramienta correcta y que se puedan romper a mano
    en creativo sin que Minecraft los trate como piedra sin picar."""
    for tag in ("mineable/pickaxe",):
        escribir(RES / f"data/minecraft/tags/block/{tag}.json",
                 {"replace": False, "values": [f"{NS}:{i}" for i in ids]})


def generar_java() -> None:
    """`Paleta.java`: la lista de colores tal y como la ve el mod.

    Se genera para que sea IMPOSIBLE que el codigo registre un color cuya
    textura no existe. Si algun dia se anade un color, se anade arriba en
    PALETA y se vuelve a ejecutar esto; tocar el .java a mano lo pisa.
    """
    filas = ",\n".join(
        f'        new Paleta("{cid}", MapColor.{mapa})'
        for cid, _, _, _, mapa in PALETA)
    JAVA.mkdir(parents=True, exist_ok=True)
    (JAVA / "Paleta.java").write_text(f"""\
package net.pokereport.neon;

import net.minecraft.block.MapColor;

/**
 * Los 16 colores del neon.
 *
 * <p><b>GENERADO por {{@code tools/gen_neon.py}} — no editar a mano.</b> La
 * lista viva esta en la constante {{@code PALETA}} de ese script, que ademas
 * dibuja las texturas y escribe los modelos. Tenerla en dos sitios es como se
 * registra un bloque cuya textura no existe.
 *
 * @param id    parte del identificador: {{@code neon_<id>}}, {{@code neon_<id>_losa}}...
 * @param mapa  color en el mapa y en la brujula de localizador
 */
public record Paleta(String id, MapColor mapa) {{

    public static final Paleta[] COLORES = {{
{filas},
    }};
}}
""", encoding="utf-8")


# ---------------------------------------------------------------------------
# VERIFICACION
#
# El equivalente aqui de la regla MOD-006 ("cada sistema anade sus invariantes
# antes de desplegarse"). El autotest del mod grande comprueba economia y base
# de datos; esto comprueba lo unico que puede romperse en un mod de bloques.
#
# Y el fallo es especialmente traicionero: el SERVIDOR no lee los assets, asi
# que arranca tan contento con un modelo roto. El error solo aparece en la
# pantalla del jugador, como el cubo negro y morado, y para entonces ya se ha
# publicado el pack.
# ---------------------------------------------------------------------------

def verificar() -> int:
    fallos = []
    a = RES / f"assets/{NS}"
    d = RES / f"data/{NS}"

    def json_de(ruta: Path):
        return json.loads(ruta.read_text(encoding="utf-8"))

    def ref(valor: str, carpeta: str, ext: str) -> Path | None:
        """Traduce `lunaneon:block/x` a un fichero. None si es de vanilla."""
        if not valor.startswith(f"{NS}:"):
            return None
        return a / carpeta / (valor.split(":", 1)[1] + ext)

    esperados = [f"neon_{cid}{sufijo}"
                 for cid, *_ in PALETA for sufijo, _, _ in FORMAS]

    es = json_de(a / "lang/es_es.json")
    en = json_de(a / "lang/en_us.json")
    modelos_usados, texturas_usadas = set(), set()

    for bid in esperados:
        estado = a / f"blockstates/{bid}.json"
        if not estado.exists():
            fallos.append(f"{bid}: sin blockstate")
            continue

        variantes = json_de(estado)["variants"]
        # Toda variante tiene que fijar `luz`, o Minecraft se queda sin modelo
        # para los estados que falten y los dibuja como cubo de textura ausente.
        sin_luz = [k for k in variantes if "luz=" not in k]
        if sin_luz:
            fallos.append(f"{bid}: {len(sin_luz)} variantes sin `luz`")

        for variante in variantes.values():
            m = ref(variante["model"], "models", ".json")
            if m is None or not m.exists():
                fallos.append(f"{bid}: modelo ausente {variante['model']}")
            else:
                modelos_usados.add(m)

        if not (d / f"loot_table/blocks/{bid}.json").exists():
            fallos.append(f"{bid}: sin loot table, se rompe y no suelta nada")
        for idioma, tabla in (("es_es", es), ("en_us", en)):
            if f"block.{NS}.{bid}" not in tabla:
                fallos.append(f"{bid}: sin nombre en {idioma}")

        item = a / f"models/item/{bid}.json"
        if not item.exists():
            fallos.append(f"{bid}: sin modelo de objeto")
        else:
            padre = ref(json_de(item)["parent"], "models", ".json")
            if padre is None or not padre.exists():
                fallos.append(f"{bid}: el modelo de objeto apunta a la nada")

    for modelo_ruta in sorted(a.glob("models/block/*.json")):
        datos = json_de(modelo_ruta)
        padre = datos.get("parent")
        if padre and (p := ref(padre, "models", ".json")) and not p.exists():
            fallos.append(f"{modelo_ruta.name}: padre ausente {padre}")
        for valor in datos.get("textures", {}).values():
            if valor.startswith("#"):
                continue
            t = ref(valor, "textures", ".png")
            if t is None or not t.exists():
                fallos.append(f"{modelo_ruta.name}: textura ausente {valor}")
            else:
                texturas_usadas.add(t)

    # Huerfanos: no rompen nada, pero engordan el jar y siempre son sintoma de
    # que algo se renombro a medias.
    for ruta in a.glob("textures/block/*.png"):
        if ruta not in texturas_usadas:
            fallos.append(f"textura huerfana: {ruta.name}")
    for ruta in a.glob("models/block/*.json"):
        if ruta not in modelos_usados:
            fallos.append(f"modelo huerfano: {ruta.name}")

    print(f"VERIFICACION  ·  {len(esperados)} bloques")
    if fallos:
        for f in fallos[:30]:
            print(f"  FALLO  {f}")
        if len(fallos) > 30:
            print(f"  ... y {len(fallos) - 30} mas")
        print(f"\n  {len(fallos)} fallos. NO desplegar.")
        return 1
    print(f"  {len(modelos_usados)} modelos y {len(texturas_usadas)} texturas, "
          f"todos referenciados y presentes")
    print("  correcto")
    return 0


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--listar", action="store_true",
                    help="imprime los IDs y no escribe nada")
    ap.add_argument("--verificar", action="store_true",
                    help="comprueba lo ya generado y no escribe nada")
    args = ap.parse_args()

    if args.verificar:
        raise SystemExit(verificar())

    if args.listar:
        for cid, *_ in PALETA:
            print("  " + "  ".join(f"neon_{cid}{s}" for s, _, _ in FORMAS))
        print(f"\n  {len(PALETA)} colores x {len(FORMAS)} formas = "
              f"{len(PALETA) * len(FORMAS)} bloques")
        return

    # Se borra antes de generar: si se quita un color de PALETA, sus ficheros
    # tienen que desaparecer. Si no, quedan modelos huerfanos apuntando a
    # bloques que ya no existen y el log del cliente se llena de avisos.
    for carpeta in (RES / f"assets/{NS}", RES / f"data/{NS}",
                    RES / "data/minecraft"):
        if carpeta.exists():
            shutil.rmtree(carpeta)

    print(f"NEON  ·  {len(PALETA)} colores x {len(FORMAS)} formas")
    ids = []
    for cid, nombre_es, _, hexa, _ in PALETA:
        generar_color(cid, hexa, ids)
        print(f"  {nombre_es:<12} {hexa}")

    generar_idiomas()
    generar_tags(ids)
    generar_java()

    n = sum(1 for _ in RES.rglob("*") if _.is_file())
    print(f"\n  -> {len(ids)} bloques, {n} ficheros en {RES.relative_to(RAIZ)}")
    print(f"  -> Paleta.java regenerado")


if __name__ == "__main__":
    main()
