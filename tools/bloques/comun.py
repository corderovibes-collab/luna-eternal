#!/usr/bin/env python3
"""Lo que comparten TODAS las familias de bloques: colores, ruido y formas.

Aqui no hay ni un bloque. Hay tres cosas, y ninguna sabe nada de las otras:

    COLOR       mezclar, aclarar, oscurecer un RGB
    RUIDO       grano y veteado ENCAJABLES: al repetir la textura no se ve
                la junta. Es la diferencia entre una fachada y un tablero de
                ajedrez de 16 en 16
    FORMAS      escribir el blockstate, los modelos, el modelo de objeto y la
                loot table de un bloque, una losa, una escalera, un muro, una
                valla, un pilar o un panel

Las formas se escriben una sola vez para todo el mod. Son las mismas 40
variantes de escalera y el mismo multipart de muro para el neon, el hormigon y
el metal, y duplicarlas es como se acaba con una escalera de hormigon que mira
al revés en las esquinas interiores y nadie se entera hasta que hay una plaza
construida encima.

Las plantillas de muro, valla y panel NO estan inventadas: se leyeron del
propio jar de Minecraft 1.21.1 y se apunta a sus modelos padre
(`minecraft:block/template_wall_post`, `fence_post`, `template_glass_pane_*`),
que es exactamente lo que hace vanilla con la piedra o el cristal.
"""
from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent.parent
MOD = RAIZ / "neon"
RES = MOD / "src" / "main" / "resources"
JAVA = MOD / "src" / "main" / "java" / "net" / "pokereport" / "neon"

NS = "lunaneon"

BLANCO = (255, 255, 255)
NEGRO = (0, 0, 0)


# ---------------------------------------------------------------------------
# COLOR
# ---------------------------------------------------------------------------

def rgb(hexa: str) -> tuple:
    hexa = hexa.lstrip("#")
    return tuple(int(hexa[i:i + 2], 16) for i in (0, 2, 4))


def mezcla(a: tuple, b: tuple, t: float) -> tuple:
    """Interpola dos colores. t=0 devuelve a, t=1 devuelve b."""
    t = max(0.0, min(1.0, t))
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def sombra(c: tuple, v: float) -> tuple:
    """Aclara (v>0) u oscurece (v<0) un color. `v` va en fraccion, no en 0-255.

    Es la operacion que hace el 90 % del dibujo: casi ninguna textura de aqui
    usa un color distinto del suyo, solo el suyo mas o menos iluminado. Por eso
    un cambio de paleta se nota en las 500 texturas sin tocar el dibujo.
    """
    if v >= 0:
        return mezcla(c, BLANCO, v)
    return mezcla(c, NEGRO, -v)


def luma(c: tuple) -> float:
    """Luminosidad percibida, 0-1. Sirve para decidir si un detalle se dibuja
    en claro o en oscuro sin tener que mirarlo."""
    return (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255.0


# ---------------------------------------------------------------------------
# RUIDO ENCAJABLE
#
# Todo el ruido de aqui es PERIODICO en 16: la columna 15 pega con la columna 0
# del bloque de al lado. Sin eso, una pared de hormigon de 40 bloques enseña la
# reticula, que es el fallo que delata una textura hecha a ojo.
# ---------------------------------------------------------------------------

def punto(x: int, y: int, semilla: int) -> float:
    """Ruido blanco determinista en 0-1. Mismo x,y,semilla -> mismo numero.

    Determinista a proposito y no `random`: regenerar el mod dos veces tiene que
    dar las mismas 500 texturas byte a byte, o cada ejecucion mueve el jar
    entero y con el la huella con la que se publica.
    """
    n = (x * 374761393 + y * 668265263 + semilla * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def grano(lado: int, semilla: int) -> list:
    """Ruido de un pixel. Devuelve una matriz [y][x] de 0-1."""
    return [[punto(x, y, semilla) for x in range(lado)] for y in range(lado)]


def veta(lado: int, celda: int, semilla: int) -> list:
    """Ruido suave: manchas del tamaño de `celda`, interpoladas.

    Es lo que da el aspecto de material y no de plastico. `celda` tiene que
    dividir a `lado`, o la textura deja de encajar consigo misma.
    """
    if lado % celda:
        raise ValueError(f"celda {celda} no divide a {lado}")
    g = lado // celda
    nodos = [[punto(i % g, j % g, semilla) for i in range(g + 1)]
             for j in range(g + 1)]
    salida = []
    for y in range(lado):
        fila = []
        fy, j = (y % celda) / celda, y // celda
        # Suavizado de Perlin: sin el se ven los rombos de la interpolacion
        # lineal, que a 16 pixeles cantan mucho.
        sy = fy * fy * (3 - 2 * fy)
        for x in range(lado):
            fx, i = (x % celda) / celda, x // celda
            sx = fx * fx * (3 - 2 * fx)
            arriba = nodos[j][i] + (nodos[j][i + 1] - nodos[j][i]) * sx
            abajo = nodos[j + 1][i] + (nodos[j + 1][i + 1] - nodos[j + 1][i]) * sx
            fila.append(arriba + (abajo - arriba) * sy)
        salida.append(fila)
    return salida


def lienzo(lado: int = 16) -> tuple:
    img = Image.new("RGBA", (lado, lado))
    return img, img.load()


# ---------------------------------------------------------------------------
# ESCRITURA
# ---------------------------------------------------------------------------

def escribir(ruta: Path, datos) -> None:
    ruta.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(datos, Image.Image):
        datos.save(ruta)
    else:
        ruta.write_text(json.dumps(datos, indent=2, ensure_ascii=False) + "\n",
                        encoding="utf-8")


class Escritor:
    """Acumula todo lo que produce una familia y lo deja en disco.

    Existe por una razon concreta: el idioma y los tags son ficheros UNICOS
    para el mod entero, pero los escriben cinco familias distintas. Si cada una
    escribiera el suyo, la ultima pisaria a las demas y los bloques de las
    primeras se llamarian `block.lunaneon.hormigon_pulido_cian` en la pantalla
    del jugador. Aqui se acumulan y `gen_bloques.py` los vuelca una vez.

    :param variantes: transformacion opcional de los estados. El neon la usa
        para multiplicar cada variante por sus tres valores de `luz`; las demas
        familias no la necesitan y pasan `None`.
    :param ao: oclusion ambiental de los modelos. El neon la apaga —una esquina
        con sombra en un bloque que se dibuja a tope se ve como un parche
        sucio—; el hormigon y el metal la quieren, que es lo que les da volumen.
    """

    def __init__(self, res: Path = RES, variantes=None, ao: bool = True):
        self.res = res
        self._variantes = variantes
        self.ao = ao
        self.ids: list[str] = []
        self.es: dict[str, str] = {}
        self.en: dict[str, str] = {}
        self.tags: dict[str, list[str]] = defaultdict(list)

    # -- ficheros ----------------------------------------------------------

    def textura(self, nombre: str, img: Image.Image) -> str:
        escribir(self.res / f"assets/{NS}/textures/block/{nombre}.png", img)
        return f"{NS}:block/{nombre}"

    def modelo(self, nombre: str, datos: dict) -> str:
        escribir(self.res / f"assets/{NS}/models/block/{nombre}.json", datos)
        return f"{NS}:block/{nombre}"

    def hereda(self, nombre: str, padre: str, texturas: dict) -> str:
        datos = {"parent": padre}
        if not self.ao:
            datos["ambientocclusion"] = False
        datos["textures"] = texturas
        return self.modelo(nombre, datos)

    def item(self, nombre: str, padre: str) -> None:
        escribir(self.res / f"assets/{NS}/models/item/{nombre}.json",
                 {"parent": padre})

    def item_plano(self, nombre: str, textura: str) -> None:
        """Modelo de objeto de un panel: en la mano es una lamina, no un cubo.
        Es lo que hace vanilla con `glass_pane`."""
        escribir(self.res / f"assets/{NS}/models/item/{nombre}.json",
                 {"parent": "minecraft:item/generated",
                  "textures": {"layer0": textura}})

    def estado(self, nombre: str, variantes: dict) -> None:
        if self._variantes is not None:
            variantes = self._variantes(variantes)
        escribir(self.res / f"assets/{NS}/blockstates/{nombre}.json",
                 {"variants": variantes})

    def multiparte(self, nombre: str, partes: list) -> None:
        # Los muros, las vallas y los paneles se dibujan sumando trozos segun a
        # que lados se conectan. No son variantes: son piezas que se apilan.
        if self._variantes is not None:
            raise ValueError(
                f"{nombre}: una familia con propiedad extra no puede usar "
                "multipart sin decidir como se combina con ella")
        escribir(self.res / f"assets/{NS}/blockstates/{nombre}.json",
                 {"multipart": partes})

    def botin(self, nombre: str, doble: bool = False) -> None:
        """Loot table. Sin ella el bloque se rompe y no suelta nada."""
        funciones = [{"function": "minecraft:explosion_decay"}]
        if doble:
            # Una losa doble tiene que soltar dos, o partir y rehacer una pared
            # de losas destruye la mitad del material.
            funciones.insert(0, {
                "function": "minecraft:set_count", "add": False, "count": 2.0,
                "conditions": [{
                    "condition": "minecraft:block_state_property",
                    "block": f"{NS}:{nombre}",
                    "properties": {"type": "double"},
                }],
            })
        escribir(self.res / f"data/{NS}/loot_table/blocks/{nombre}.json", {
            "type": "minecraft:block",
            "random_sequence": f"{NS}:blocks/{nombre}",
            "pools": [{
                "rolls": 1.0, "bonus_rolls": 0.0,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{nombre}",
                             "functions": funciones}],
            }],
        })

    # -- alta ---------------------------------------------------------------

    def alta(self, bid: str, es: str, en: str, *tags: str,
             herramienta: str | None = "minecraft:mineable/pickaxe") -> None:
        """Da de alta un bloque: su nombre en los dos idiomas y sus tags.

        Los tags no son decoracion. `minecraft:walls` es lo que hace que dos
        muros se PEGUEN entre si: sin el, cada muro se queda solo como un poste
        y una valla de veinte metros son veinte postes sueltos.

        :param herramienta: `None` para el vidrio. Vanilla no mete el cristal en
            ninguna familia de herramienta, y hace bien: no se pica, se rompe.
        """
        self.ids.append(bid)
        self.es[f"block.{NS}.{bid}"] = es
        self.en[f"block.{NS}.{bid}"] = en
        if herramienta:
            self.tags[herramienta].append(bid)
        for tag in tags:
            self.tags[tag].append(bid)


# ---------------------------------------------------------------------------
# LA TABLA DE LAS ESCALERAS
#
# Copiada TAL CUAL de assets/minecraft/blockstates/oak_stairs.json del jar
# 1.21.1. Son 40 combinaciones de facing/half/shape con sus rotaciones;
# deducirlas a mano es la clase de tarea que sale mal en dos de las cuarenta y
# no te enteras hasta que alguien pone una escalera mirando al reves en una
# esquina.
#   clave -> (variante_de_modelo, x, y)   x/y = None si no lleva rotacion
# ---------------------------------------------------------------------------
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

# Las cuatro orientaciones de un lateral de muro, valla o panel. La misma pieza
# girada; `y=None` es "sin girar", que no es lo mismo que `y=0` porque este
# ultimo se escribiria en el JSON y vanilla no lo hace.
LADOS = (("north", None), ("east", 90), ("south", 180), ("west", 270))


# ---------------------------------------------------------------------------
# LAS FORMAS
#
# Cada una escribe lo suyo y devuelve nada. Todas reciben el mismo trio:
#   w    el Escritor
#   bid  el identificador del bloque ya completo (`hormigon_pulido_cian_losa`)
#   tex  el identificador de la textura (`lunaneon:block/hormigon_pulido_cian`)
# ---------------------------------------------------------------------------

def forma_bloque(w: Escritor, bid: str, tex: str) -> None:
    w.hereda(bid, "minecraft:block/cube_all", {"all": tex})
    w.item(bid, f"{NS}:block/{bid}")
    w.estado(bid, {"": {"model": f"{NS}:block/{bid}"}})
    w.botin(bid)


def forma_bloque_columna(w: Escritor, bid: str, lado: str, tapa: str) -> None:
    """Cubo con las tapas distintas de los lados, SIN eje. Para el estriado y
    la rejilla: el suelo de un pasillo tecnico no se ve igual que su pared."""
    w.hereda(bid, "minecraft:block/cube_column", {"end": tapa, "side": lado})
    w.item(bid, f"{NS}:block/{bid}")
    w.estado(bid, {"": {"model": f"{NS}:block/{bid}"}})
    w.botin(bid)


def forma_losa(w: Escritor, bid: str, tex: str, entero: str | None = None) -> None:
    """:param entero: modelo del bloque completo, para `type=double`."""
    caras = {"bottom": tex, "top": tex, "side": tex}
    w.hereda(bid, "minecraft:block/slab", caras)
    w.hereda(f"{bid}_arriba", "minecraft:block/slab_top", caras)
    w.item(bid, f"{NS}:block/{bid}")
    w.estado(bid, {
        "type=bottom": {"model": f"{NS}:block/{bid}"},
        "type=top":    {"model": f"{NS}:block/{bid}_arriba"},
        "type=double": {"model": entero or f"{NS}:block/{bid}_doble"},
    })
    if entero is None:
        w.hereda(f"{bid}_doble", "minecraft:block/cube_all", {"all": tex})
    w.botin(bid, doble=True)


def forma_escalera(w: Escritor, bid: str, tex: str) -> None:
    caras = {"bottom": tex, "top": tex, "side": tex}
    for sufijo, padre in (("", "stairs"), ("_interior", "inner_stairs"),
                          ("_exterior", "outer_stairs")):
        w.hereda(f"{bid}{sufijo}", f"minecraft:block/{padre}", caras)
    w.item(bid, f"{NS}:block/{bid}")
    variantes = {}
    for (facing, half, shape), (sufijo, x, y) in ESCALERAS.items():
        v = {"model": f"{NS}:block/{bid}{sufijo}"}
        if x is not None:
            v["x"] = x
        if y is not None:
            v["y"] = y
        if x is not None or y is not None:
            v["uvlock"] = True
        variantes[f"facing={facing},half={half},shape={shape}"] = v
    w.estado(bid, variantes)
    w.botin(bid)


def forma_muro(w: Escritor, bid: str, tex: str) -> None:
    """Muro. Tres piezas —poste, lateral bajo y lateral alto— sumadas segun a
    donde se pega. Las plantillas son las de vanilla."""
    w.hereda(f"{bid}_poste", "minecraft:block/template_wall_post", {"wall": tex})
    w.hereda(f"{bid}_lado", "minecraft:block/template_wall_side", {"wall": tex})
    w.hereda(f"{bid}_lado_alto", "minecraft:block/template_wall_side_tall",
             {"wall": tex})
    w.hereda(f"{bid}_inventario", "minecraft:block/wall_inventory", {"wall": tex})
    w.item(bid, f"{NS}:block/{bid}_inventario")

    partes = [{"apply": {"model": f"{NS}:block/{bid}_poste"}, "when": {"up": "true"}}]
    for altura, pieza in (("low", "_lado"), ("tall", "_lado_alto")):
        for lado, giro in LADOS:
            apply = {"model": f"{NS}:block/{bid}{pieza}", "uvlock": True}
            if giro is not None:
                apply["y"] = giro
            partes.append({"apply": apply, "when": {lado: altura}})
    w.multiparte(bid, partes)
    w.botin(bid)


def forma_valla(w: Escritor, bid: str, tex: str) -> None:
    w.hereda(f"{bid}_poste", "minecraft:block/fence_post", {"texture": tex})
    w.hereda(f"{bid}_lado", "minecraft:block/fence_side", {"texture": tex})
    w.hereda(f"{bid}_inventario", "minecraft:block/fence_inventory",
             {"texture": tex})
    w.item(bid, f"{NS}:block/{bid}_inventario")

    partes = [{"apply": {"model": f"{NS}:block/{bid}_poste"}}]
    for lado, giro in LADOS:
        apply = {"model": f"{NS}:block/{bid}_lado", "uvlock": True}
        if giro is not None:
            apply["y"] = giro
        partes.append({"apply": apply, "when": {lado: "true"}})
    w.multiparte(bid, partes)
    w.botin(bid)


def forma_pilar(w: Escritor, bid: str, lado: str, tapa: str) -> None:
    caras = {"end": tapa, "side": lado}
    w.hereda(bid, "minecraft:block/cube_column", caras)
    w.hereda(f"{bid}_h", "minecraft:block/cube_column_horizontal", caras)
    w.item(bid, f"{NS}:block/{bid}")
    w.estado(bid, {
        "axis=y": {"model": f"{NS}:block/{bid}"},
        "axis=z": {"model": f"{NS}:block/{bid}_h", "x": 90},
        "axis=x": {"model": f"{NS}:block/{bid}_h", "x": 90, "y": 90},
    })
    w.botin(bid)


def forma_panel(w: Escritor, bid: str, tex: str, canto: str) -> None:
    """Panel vertical que se pega a sus vecinos: el cristal de una fachada, la
    barandilla de un balcon, la reja de un patio tecnico.

    :param canto: textura del borde, la franja que se ve de perfil. Vanilla usa
        una tira aparte (`glass_pane_top`) y por eso el cristal no parece papel.
    """
    caras = {"pane": tex, "edge": canto}
    for pieza, padre in (
            ("_poste", "template_glass_pane_post"),
            ("_lado", "template_glass_pane_side"),
            ("_lado_alt", "template_glass_pane_side_alt"),
            ("_suelto", "template_glass_pane_noside"),
            ("_suelto_alt", "template_glass_pane_noside_alt")):
        w.hereda(f"{bid}{pieza}", f"minecraft:block/{padre}", caras)
    w.item_plano(bid, tex)

    partes = [{"apply": {"model": f"{NS}:block/{bid}_poste"}}]
    # El orden y los giros son los de `glass_pane.json`, incluido el detalle de
    # que la cara "sin lado" del oeste va girada 270 y no 90.
    for lado, pieza, giro in (
            ("north", "_lado", None), ("east", "_lado", 90),
            ("south", "_lado_alt", None), ("west", "_lado_alt", 90)):
        apply = {"model": f"{NS}:block/{bid}{pieza}"}
        if giro is not None:
            apply["y"] = giro
        partes.append({"apply": apply, "when": {lado: "true"}})
    for lado, pieza, giro in (
            ("north", "_suelto", None), ("east", "_suelto_alt", None),
            ("south", "_suelto_alt", 90), ("west", "_suelto", 270)):
        apply = {"model": f"{NS}:block/{bid}{pieza}"}
        if giro is not None:
            apply["y"] = giro
        partes.append({"apply": apply, "when": {lado: "false"}})
    w.multiparte(bid, partes)
    w.botin(bid)
