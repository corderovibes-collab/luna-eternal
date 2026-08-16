#!/usr/bin/env python3
"""Las cinco familias de obra de la ciudadela: 506 bloques.

    HORMIGON   3 acabados x 16 colores x 5 formas   240
    METAL      8 aleaciones x 4 acabados x 5 formas 160
    REJILLA    8 aleaciones x 3 formas               24
    VIDRIO     2 acabados x 16 colores x 2 formas    64
    PAVIMENTO  6 tipos x 3 formas                    18

POR QUE HACEN FALTA, Y NO ES POR TENER MAS

Vanilla no tiene ni una escalera, ni una losa, ni un muro, ni una valla de
hormigon. Tiene el cubo y se acaba ahi. Para una ciudad moderna eso significa
que en cuanto hay que rematar un borde, una cornisa, un parapeto o una rampa,
hay que salirse a la piedra o al ladrillo — y la fachada deja de leerse como
hormigon. Es el agujero mas grande de vanilla para construir moderno, y por eso
existen mods enteros dedicados solo a taparlo.

Metal, directamente, no hay: hay bloque de hierro y bloque de cobre. Ni chapa
lisa, ni cepillado, ni pletina estriada, ni panel remachado, ni rejilla.

POR QUE LAS TEXTURAS SE DIBUJAN AQUI Y NO SE BAJAN DE NINGUN SITIO

Por licencia (D-008). Casi todo el arte de bloques que circula por GitHub y
CurseForge es ARR o CC-BY-NC, y el NC choca de frente con el plan de vender
paquetes (D-007): es exactamente la clausula que descarto CobbleVerse (D-006).
Dibujarlas cuesta este fichero y no hipoteca nada.

Y sale ganando: los 16 colores del hormigon y del vidrio son LOS MISMOS 16 del
neon, rebajados a tono de obra por una formula. Un neon cian pega con un
hormigon cian porque son el mismo color, no porque alguien los haya emparejado
a ojo.

REGLA DE DIBUJO: CONTRASTE BAJO

Una fachada moderna son superficies grandes y planas. Todo el relieve de aqui
va entre el 2 % y el 20 % de variacion, y solo pasa del 20 % donde hay una
junta o un remache de verdad. Con mas ruido el bloque se lee como piedra
rustica, que es justo lo contrario de lo que se busca.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

from . import comun as C
from .comun import (NS, Escritor, forma_bloque, forma_escalera, forma_losa,
                    forma_muro, forma_panel, forma_pilar, forma_valla, grano,
                    lienzo, luma, mezcla, rgb, sombra, veta)
from .neon import PALETA


@dataclass
class Material:
    """Una fila de la tabla que acaba en `Catalogo.java`."""
    id: str
    familia: str
    mapa: str
    formas: list = field(default_factory=list)


# ---------------------------------------------------------------------------
# EL COLOR DE OBRA
# ---------------------------------------------------------------------------

def a_luma(c: tuple, objetivo: float) -> tuple:
    """Reescala un color hasta que su luminosidad sea `objetivo` (0-1)."""
    actual = luma(c)
    if actual < 0.004:
        v = round(objetivo * 255)
        return (v, v, v)
    k = objetivo / actual
    return tuple(min(255, round(x * k)) for x in c)


def tono_obra(c: tuple) -> tuple:
    """De un neon a su hormigon.

    Dos pasos, y los dos importan:

    1. **Se le quita el 60 % de saturacion.** Un cian de neon en una pared de
       veinte metros es insoportable; el mismo cian al 40 % es un edificio.
    2. **Se le comprime el valor al tramo 0,16-0,78.** Sin esto, el «hormigon
       blanco» saldria papel y el «negro» carbon, y ninguno de los dos es un
       material: son los extremos donde se pierde todo el relieve.

    Lo que NO se toca es el TONO, y es la razon de ser de la funcion: el
    hormigon cian sigue siendo el mismo cian que el neon cian.
    """
    g = luma(c)
    gris = tuple(round(g * 255) for _ in range(3))
    base = mezcla(c, gris, 0.60)
    return a_luma(base, 0.16 + g * 0.62)


def tono_vidrio(c: tuple) -> tuple:
    """El vidrio conserva mas color que el hormigon: se ve por transparencia y
    un cristal desaturado no se distingue de otro."""
    return a_luma(mezcla(c, tuple(round(luma(c) * 255) for _ in range(3)), 0.20),
                  max(0.28, min(0.86, luma(c) * 0.92 + 0.10)))


# ---------------------------------------------------------------------------
# LAS TABLAS
# ---------------------------------------------------------------------------

# Hormigon: tres acabados que vanilla no tiene y que se distinguen A DIEZ
# BLOQUES, que es la prueba que importa. Un cuarto acabado «liso» se descarto
# por eso: a esa distancia era el hormigon de vanilla.
HORMIGON = [
    # id          es              en                dibujo
    ("pulido",   "pulido",       "Polished",       "pulido"),
    ("rayado",   "rayado",       "Fluted",         "rayado"),
    ("panel",    "en panel",     "Panelled",       "panel"),
]

# Aleaciones. `brillo` multiplica el contraste del acabado: el cromo marca las
# rayas del cepillado mucho mas que el grafito, y esa es la mitad de lo que
# hace que un metal parezca ese metal y no gris pintado.
ALEACIONES = [
    # id             es              en             base       brillo  MapColor
    ("acero",        "Acero",        "Steel",       "#8E97A2", 1.00, "IRON_GRAY"),
    ("acero_oscuro", "Acero oscuro", "Dark Steel",  "#464D58", 0.95, "DEEPSLATE_GRAY"),
    ("aluminio",     "Aluminio",     "Aluminio",    "#B8C0C8", 1.05, "WHITE_GRAY"),
    ("titanio",      "Titanio",      "Titanium",    "#6E7B8B", 1.00, "STONE_GRAY"),
    ("cromo",        "Cromo",        "Chrome",      "#D5DDE5", 1.25, "WHITE_GRAY"),
    ("cobre",        "Cobre",        "Copper",      "#C07A40", 1.00, "ORANGE"),
    ("laton",        "Laton",        "Brass",       "#C5A146", 1.00, "GOLD"),
    ("grafito",      "Grafito",      "Graphite",    "#2C303A", 0.80, "BLACK"),
]

ACABADOS_METAL = [
    # id            es              en             dibujo
    ("liso",       "liso",         "Smooth",       "liso"),
    ("cepillado",  "cepillado",    "Brushed",      "cepillado"),
    ("estriado",   "estriado",     "Treadplate",   "estriado"),
    ("remachado",  "remachado",    "Riveted",      "remachado"),
]

VIDRIOS = [
    # id             es                en           alfa campo, alfa marco
    ("claro",       "Vidrio",         "Glass",      46, 132),
    ("polarizado",  "Vidrio polarizado", "Tinted Glass", 168, 216),
]

PAVIMENTOS = [
    # id              es                en                 base       dibujo     MapColor
    ("asfalto",       "Asfalto",        "Asphalt",         "#3A3D43", "asfalto",  "BLACK"),
    ("asfalto_claro", "Asfalto claro",  "Light Asphalt",   "#5B5F67", "asfalto",  "DEEPSLATE_GRAY"),
    ("terrazo_claro", "Terrazo claro",  "Light Terrazzo",  "#CDD2D8", "terrazo",  "WHITE_GRAY"),
    ("terrazo_oscuro","Terrazo oscuro", "Dark Terrazzo",   "#4C515B", "terrazo",  "STONE_GRAY"),
    ("losa_grande",   "Losa grande",    "Large Paving",    "#9AA0A8", "losa",     "STONE_GRAY"),
    ("adoquin_fino",  "Adoquin fino",   "Fine Setts",      "#757B85", "adoquin",  "STONE_GRAY"),
]

# Las formas de cada familia. No son las mismas a proposito:
#
#   el hormigon lleva MURO porque un parapeto de azotea es hormigon, y el metal
#   lleva PILAR porque una viga es metal. Al reves no se usan, y cada forma que
#   no se usa son cuatro modelos mas en el jar y una linea mas de scroll en el
#   inventario para todo el mundo.
FORMAS_HORMIGON = ["", "_losa", "_escalera", "_muro", "_valla"]
FORMAS_METAL = ["", "_losa", "_escalera", "_pilar", "_valla"]
FORMAS_REJILLA = ["", "_losa", "_panel"]
FORMAS_VIDRIO = ["", "_panel"]
FORMAS_PAVIMENTO = ["", "_losa", "_escalera"]

# Nombre de cada forma en los dos idiomas.
#   {m}  el material con mayuscula:  "Hormigón pulido cian"
#   {mb} el mismo en minuscula:      "hormigón pulido cian"
#   {c}  el material en ingles:      "Polished Cyan Concrete"
NOMBRES = {
    "":          ("{m}",                 "{c}"),
    "_losa":     ("Losa de {mb}",        "{c} Slab"),
    "_escalera": ("Escalera de {mb}",    "{c} Stairs"),
    "_muro":     ("Muro de {mb}",        "{c} Wall"),
    "_valla":    ("Valla de {mb}",       "{c} Fence"),
    "_pilar":    ("Pilar de {mb}",       "{c} Pillar"),
    "_panel":    ("Panel de {mb}",       "{c} Pane"),
}

# Los tags que hacen que una forma se PEGUE a las de su clase. Sin
# `minecraft:walls`, dos muros nuestros se ignoran y una barandilla de veinte
# metros son veinte postes sueltos; sin `minecraft:fences`, igual con las
# vallas. `stairs` y `slabs` no cambian el dibujo pero si el comportamiento de
# recetas y de otros mods, y salen gratis.
TAGS = {
    "_muro":     ("minecraft:walls",),
    "_valla":    ("minecraft:fences",),
    "_escalera": ("minecraft:stairs",),
    "_losa":     ("minecraft:slabs",),
}


# ---------------------------------------------------------------------------
# TEXTURAS · HORMIGON
# ---------------------------------------------------------------------------

def tex_hormigon_pulido(c: tuple, s: int):
    """Hormigon visto y pulido: grano fino, veteado amplio y algun poro.

    Los poros son poco mas del 1 % de los pixeles y son lo unico que impide que
    una pared grande parezca una superficie de plastico. Empezaron en el 3 % y
    en la lamina de contacto se leian como una salpicadura de puntos: a esta
    escala, tres pixeles oscuros por bloque ya son un dibujo.
    """
    img, px = lienzo()
    fino, manto, poros = grano(16, s), veta(16, 8, s + 1), grano(16, s + 2)
    for y in range(16):
        for x in range(16):
            v = (fino[y][x] - 0.5) * 0.035 + (manto[y][x] - 0.5) * 0.080
            p = poros[y][x]
            if p > 0.986:
                v -= 0.075         # poro
            elif p < 0.014:
                v += 0.055         # arido claro aflorando
            px[x, y] = (*sombra(c, v), 255)
    return img


# Perfil de una nervadura de 4 px: junta, filo iluminado, cuerpo, caida.
# Sale de mirar un hormigon nervado real con luz cenital, y es lo que convierte
# una pared plana en una fachada con ritmo vertical.
PERFIL_RAYA = (-0.155, 0.130, 0.045, -0.055)


def tex_hormigon_rayado(c: tuple, s: int):
    """Hormigon nervado: cuatro nervaduras verticales de 4 px."""
    img, px = lienzo()
    fino, manto = grano(16, s), veta(16, 8, s + 1)
    for x in range(16):
        r = PERFIL_RAYA[x % 4]
        for y in range(16):
            v = r + (fino[y][x] - 0.5) * 0.030 + (manto[y][x] - 0.5) * 0.040
            px[x, y] = (*sombra(c, v), 255)
    return img


# Los cuatro tornillos del panel, hacia dentro de cada esquina.
TORNILLOS = ((3, 3), (12, 3), (3, 12), (12, 12))


def tex_hormigon_panel(c: tuple, s: int):
    """Panel prefabricado: junta hundida alrededor y cuatro tornillos.

    Al repetirse, las juntas de dos bloques vecinos suman 2 px y se lee como
    una retícula de placas de un metro — que es exactamente como se cierra una
    fachada moderna.
    """
    img, px = lienzo()
    fino, manto = grano(16, s), veta(16, 8, s + 1)
    for y in range(16):
        for x in range(16):
            m = min(x, y, 15 - x, 15 - y)
            v = (fino[y][x] - 0.5) * 0.028 + (manto[y][x] - 0.5) * 0.050
            if m == 0:
                v -= 0.185                       # la junta
            elif m == 1:
                # El filo solo se ilumina arriba y a la izquierda: la luz de la
                # ciudadela viene de la luna, y una placa iluminada por los
                # cuatro lados se ve como una pegatina.
                v += 0.075 if (y == 1 or x == 1) else -0.030
            px[x, y] = (*sombra(c, v), 255)
    for tx, ty in TORNILLOS:
        px[tx, ty] = (*sombra(c, -0.20), 255)
        px[tx, ty - 1] = (*sombra(c, 0.115), 255)
    return img


# ---------------------------------------------------------------------------
# TEXTURAS · METAL
# ---------------------------------------------------------------------------

def tex_metal_liso(c: tuple, k: float, s: int):
    """Chapa lisa: casi plana, con un brillo diagonal muy tenue.

    El brillo va en `sin(2*pi*(x+y)/16)`, que es periodico en los dos ejes: al
    repetirse no aparece ninguna junta. Un degradado de arriba abajo, que es lo
    primero que uno escribe, dejaria una raya cada 16 px.
    """
    img, px = lienzo()
    fino = grano(16, s)
    for y in range(16):
        for x in range(16):
            v = 0.042 * k * math.sin(2 * math.pi * (x + y) / 16.0)
            v += (fino[y][x] - 0.5) * 0.024 * k
            px[x, y] = (*sombra(c, v), 255)
    return img


def tex_metal_cepillado(c: tuple, k: float, s: int):
    """Acero cepillado: rayas horizontales finas, como salido de la lijadora."""
    img, px = lienzo()
    for y in range(16):
        banda = (C.punto(0, y, s) - 0.5) * 0.105 * k
        for x in range(16):
            v = banda
            v += (C.punto(x, y, s + 1) - 0.5) * 0.032 * k
            v += (C.punto(x // 4, y, s + 2) - 0.5) * 0.022 * k
            px[x, y] = (*sombra(c, v), 255)
    return img


# Las cuatro pletinas de la chapa estriada: (x, y, direccion). Van al tresbolillo
# —dos arriba, dos abajo, desplazadas— que es como se fabrica de verdad y lo que
# evita que se lean como una reja.
PLETINAS = ((1, 1, +1), (9, 5, -1), (1, 9, -1), (9, 13, +1))
LARGO_PLETINA = 6


def tex_metal_estriado(c: tuple, k: float, s: int):
    """Chapa estriada (pletina antideslizante). Suelo tecnico, rampas, pasarelas."""
    img, px = lienzo()
    fino = grano(16, s)
    for y in range(16):
        for x in range(16):
            v = (fino[y][x] - 0.5) * 0.030 * k - 0.020
            px[x, y] = (*sombra(c, v), 255)
    for x0, y0, dy in PLETINAS:
        for i in range(LARGO_PLETINA):
            x, y = (x0 + i) % 16, (y0 + dy * i) % 16
            px[x, y] = (*sombra(c, 0.215 * k), 255)          # el filo
            px[x, (y + dy) % 16] = (*sombra(c, -0.175 * k), 255)  # su sombra
    return img


# Remaches: las cuatro esquinas y el centro de cada lado.
REMACHES = ((2, 2), (8, 2), (13, 2), (2, 8), (13, 8), (2, 13), (8, 13), (13, 13))


def tex_metal_remachado(c: tuple, k: float, s: int):
    """Panel remachado: costura hundida alrededor y ocho remaches."""
    img, px = lienzo()
    fino, manto = grano(16, s), veta(16, 8, s + 1)
    for y in range(16):
        for x in range(16):
            m = min(x, y, 15 - x, 15 - y)
            v = (fino[y][x] - 0.5) * 0.026 * k + (manto[y][x] - 0.5) * 0.045 * k
            if m == 0:
                v -= 0.155 * k
            elif m == 1:
                v += 0.070 * k if (y == 1 or x == 1) else -0.025 * k
            px[x, y] = (*sombra(c, v), 255)
    for rx, ry in REMACHES:
        px[rx, ry] = (*sombra(c, 0.185 * k), 255)
        px[rx, (ry + 1) % 16] = (*sombra(c, -0.165 * k), 255)
    return img


DIBUJOS_METAL = {
    "liso": tex_metal_liso,
    "cepillado": tex_metal_cepillado,
    "estriado": tex_metal_estriado,
    "remachado": tex_metal_remachado,
}


# ---------------------------------------------------------------------------
# TEXTURAS · REJILLA
# ---------------------------------------------------------------------------

def tex_rejilla(c: tuple, k: float, s: int):
    """Rejilla de pletina: barras portantes cada 4 px y varilla cruzada cada 8.

    Los huecos son alfa 0 de verdad, no un color oscuro: se ve el suelo a
    traves. Es lo que la hace util para pasarelas, patios tecnicos y falsos
    techos, y lo que obliga a dibujarla en capa `cutout` en el cliente.
    """
    img, px = lienzo()
    fino = grano(16, s)
    for y in range(16):
        for x in range(16):
            barra = (x % 4) < 2
            varilla = (y % 8) == 0
            if not (barra or varilla):
                px[x, y] = (0, 0, 0, 0)
                continue
            v = (fino[y][x] - 0.5) * 0.030 * k
            if barra and (x % 4) == 0:
                v += 0.135 * k          # el canto iluminado de cada pletina
            elif barra:
                v -= 0.075 * k
            if varilla:
                v += 0.055 * k
            px[x, y] = (*sombra(c, v), 255)
    return img


# ---------------------------------------------------------------------------
# TEXTURAS · VIDRIO
# ---------------------------------------------------------------------------

def tex_vidrio(c: tuple, alfa_campo: int, alfa_marco: int, s: int):
    """Vidrio moderno: casi todo campo limpio y un marco fino.

    Lo que lo separa del cristal tenido de vanilla es que aqui el campo es de
    verdad transparente. En una torre de veinte plantas eso es la diferencia
    entre ver el interior iluminado y ver una pared de color.

    NO LLEVA DESTELLO, y hubo uno. Tres pixeles claros en diagonal, para
    simular un reflejo. En la lamina de contacto se veian como tres motas de
    suciedad repetidas en cada bloque — que es exactamente el defecto que ya
    costo una noche entera en el PokePad. Un reflejo pintado en la textura se
    repite identico en las mil ventanas de una torre, y un reflejo que se
    repite mil veces no es un reflejo: es una mancha.
    """
    img, px = lienzo()
    manto = veta(16, 8, s)
    campo = sombra(c, 0.18)
    for y in range(16):
        for x in range(16):
            m = min(x, y, 15 - x, 15 - y)
            if m == 0:
                px[x, y] = (*sombra(c, -0.12), alfa_marco)
            elif m == 1:
                px[x, y] = (*sombra(c, 0.06), min(255, alfa_campo + 34))
            else:
                v = (manto[y][x] - 0.5) * 0.10
                px[x, y] = (*sombra(campo, v), alfa_campo)
    return img


def tex_canto(c: tuple):
    """El canto del panel: la franja que se ve de perfil.

    Vanilla usa una tira aparte (`glass_pane_top`) y por eso su cristal no
    parece papel. Va casi opaca a proposito: un canto transparente hace que el
    panel desaparezca visto de lado.
    """
    img, px = lienzo()
    for y in range(16):
        for x in range(16):
            px[x, y] = (*sombra(c, -0.10 + (0.06 if y < 8 else 0.0)), 232)
    return img


# ---------------------------------------------------------------------------
# TEXTURAS · PAVIMENTO
# ---------------------------------------------------------------------------

def tex_asfalto(c: tuple, s: int):
    """Aglomerado: grano grueso y arido de varios tonos."""
    img, px = lienzo()
    fino, medio, manto = grano(16, s), grano(16, s + 1), veta(16, 4, s + 2)
    for y in range(16):
        for x in range(16):
            v = (fino[y][x] - 0.5) * 0.085 + (manto[y][x] - 0.5) * 0.055
            if medio[y][x] > 0.955:
                v += 0.115         # una piedra clara
            elif medio[y][x] < 0.045:
                v -= 0.100
            px[x, y] = (*sombra(c, v), 255)
    return img


# Los aridos del terrazo. Son tonos, no colores: un terrazo con chinas de
# colores se lee como confeti a los tres bloques de distancia.
CHINAS = (0.26, -0.24, 0.15, -0.13, 0.34)


def tex_terrazo(c: tuple, s: int):
    """Terrazo: chinas de marmol sobre pasta. El suelo de un vestibulo."""
    img, px = lienzo()
    fino, sitio, cual = grano(16, s), grano(16, s + 1), grano(16, s + 2)
    for y in range(16):
        for x in range(16):
            v = (fino[y][x] - 0.5) * 0.030
            if sitio[y][x] > 0.72:
                v += CHINAS[int(cual[y][x] * len(CHINAS)) % len(CHINAS)]
            px[x, y] = (*sombra(c, v), 255)
    return img


def tex_losa_grande(c: tuple, s: int):
    """Losa de un metro: una sola pieza por bloque, con la junta en dos lados.

    La junta va solo arriba y a la izquierda para que al repetirse sea de 1 px
    y no de 2. Una junta de 2 px a esta escala se lee como un canal.
    """
    img, px = lienzo()
    fino, manto = grano(16, s), veta(16, 8, s + 1)
    for y in range(16):
        for x in range(16):
            v = (fino[y][x] - 0.5) * 0.032 + (manto[y][x] - 0.5) * 0.070
            if x == 0 or y == 0:
                v -= 0.165
            elif x == 1 or y == 1:
                v += 0.055
            px[x, y] = (*sombra(c, v), 255)
    return img


def tex_adoquin(c: tuple, s: int):
    """Adoquin fino: piezas de 4x4 al tresbolillo, como una acera de verdad."""
    img, px = lienzo()
    fino = grano(16, s)
    for y in range(16):
        # Cada franja de 4 px se desplaza 2: sin ese desfase se ven cruces de
        # cuatro juntas, que no existen en ninguna acera.
        desfase = 2 if (y // 4) % 2 else 0
        for x in range(16):
            xa = (x + desfase) % 16
            junta = (xa % 4) == 0 or (y % 4) == 0
            pieza = ((xa // 4) * 4 + (y // 4)) % 7
            v = (fino[y][x] - 0.5) * 0.045 + (pieza - 3) * 0.017
            if junta:
                v -= 0.150
            px[x, y] = (*sombra(c, v), 255)
    return img


DIBUJOS_PAVIMENTO = {
    "asfalto": tex_asfalto,
    "terrazo": tex_terrazo,
    "losa": tex_losa_grande,
    "adoquin": tex_adoquin,
}


# ---------------------------------------------------------------------------
# GENERACION
# ---------------------------------------------------------------------------

def escritor() -> Escritor:
    """La obra SI quiere oclusion ambiental: es lo que le da volumen a una
    cornisa. Y no tiene propiedades extra, asi que sus estados van tal cual."""
    return Escritor(variantes=None, ao=True)


def generar(w: Escritor) -> list[Material]:
    materiales: list[Material] = []
    materiales += _hormigon(w)
    materiales += _metal(w)
    materiales += _rejilla(w)
    materiales += _vidrio(w)
    materiales += _pavimento(w)
    return materiales


def _formas(w: Escritor, bid: str, tex: str, formas: list, nombre_es: str,
            nombre_en: str, canto: str | None = None,
            tex_lado: str | None = None,
            herramienta: str | None = "minecraft:mineable/pickaxe") -> None:
    """Escribe las formas pedidas de un material y las da de alta.

    :param canto:    textura del borde, obligatoria si hay `_panel`
    :param tex_lado: textura lateral, si el pilar no usa la misma que el cubo
    """
    for sufijo in formas:
        pieza = f"{bid}{sufijo}"
        if sufijo == "":
            forma_bloque(w, pieza, tex)
        elif sufijo == "_losa":
            forma_losa(w, pieza, tex, entero=f"{NS}:block/{bid}")
        elif sufijo == "_escalera":
            forma_escalera(w, pieza, tex)
        elif sufijo == "_muro":
            forma_muro(w, pieza, tex)
        elif sufijo == "_valla":
            forma_valla(w, pieza, tex)
        elif sufijo == "_pilar":
            forma_pilar(w, pieza, tex_lado or tex, tex)
        elif sufijo == "_panel":
            forma_panel(w, pieza, tex, canto or tex)
        else:
            raise ValueError(f"forma desconocida: {sufijo}")

        plantilla_es, plantilla_en = NOMBRES[sufijo]
        w.alta(pieza,
               plantilla_es.format(m=nombre_es, mb=_minuscula(nombre_es)),
               plantilla_en.format(c=nombre_en),
               *TAGS.get(sufijo, ()),
               herramienta=herramienta)


def _minuscula(nombre: str) -> str:
    """«Hormigón pulido cian» -> «hormigón pulido cian».

    Solo la primera letra: `lower()` a secas convertiria «Acero» en «acero»
    pero tambien destrozaria cualquier nombre propio que se añada despues.
    """
    return nombre[:1].lower() + nombre[1:]


def _hormigon(w: Escritor) -> list[Material]:
    # El color por FUERA y el acabado por dentro. Ese orden es el que acaba en
    # la pestaña del inventario, y se construye eligiendo primero la paleta y
    # despues la pieza — no al reves. Es la misma decision que ya se tomo con el
    # neon, y la razon por la que ahora pegan: los quince hormigones cianes caen
    # juntos, al lado de los seis neones cianes.
    salida = []
    for j, (cid, col_es, col_en, hexa, mapa) in enumerate(PALETA):
        for i, (aid, ac_es, ac_en, dibujo) in enumerate(HORMIGON):
            pintar = {"pulido": tex_hormigon_pulido,
                      "rayado": tex_hormigon_rayado,
                      "panel": tex_hormigon_panel}[dibujo]
            bid = f"hormigon_{aid}_{cid}"
            c = tono_obra(rgb(hexa))
            tex = w.textura(bid, pintar(c, 1000 + i * 97 + j * 13))
            _formas(w, bid, tex, FORMAS_HORMIGON,
                    f"Hormigón {ac_es} {_minuscula(col_es)}",
                    f"{ac_en} {col_en} Concrete")
            salida.append(Material(bid, "HORMIGON", mapa, FORMAS_HORMIGON))
    return salida


def _vidrio(w: Escritor) -> list[Material]:
    salida = []
    for j, (cid, col_es, col_en, hexa, mapa) in enumerate(PALETA):
        for i, (vid, v_es, v_en, alfa_campo, alfa_marco) in enumerate(VIDRIOS):
            bid = f"vidrio_{vid}_{cid}"
            c = tono_vidrio(rgb(hexa))
            tex = w.textura(bid, tex_vidrio(c, alfa_campo, alfa_marco,
                                            4000 + i * 53 + j * 17))
            canto = w.textura(f"{bid}_canto", tex_canto(c))
            _formas(w, bid, tex, FORMAS_VIDRIO,
                    f"{v_es} {_minuscula(col_es)}", f"{col_en} {v_en}",
                    canto=canto, herramienta=None)
            salida.append(Material(bid, "VIDRIO", mapa, FORMAS_VIDRIO))
    return salida


def _metal(w: Escritor) -> list[Material]:
    salida = []
    for i, (mid, met_es, met_en, hexa, k, mapa) in enumerate(ALEACIONES):
        c = rgb(hexa)
        for j, (aid, ac_es, ac_en, dibujo) in enumerate(ACABADOS_METAL):
            bid = f"metal_{mid}_{aid}"
            tex = w.textura(bid, DIBUJOS_METAL[dibujo](c, k, 2000 + i * 89 + j * 31))
            _formas(w, bid, tex, FORMAS_METAL,
                    f"{met_es} {ac_es}", f"{ac_en} {met_en}")
            salida.append(Material(bid, "METAL", mapa, FORMAS_METAL))
    return salida


def _rejilla(w: Escritor) -> list[Material]:
    salida = []
    for i, (mid, met_es, met_en, hexa, k, mapa) in enumerate(ALEACIONES):
        bid = f"rejilla_{mid}"
        c = rgb(hexa)
        tex = w.textura(bid, tex_rejilla(c, k, 3000 + i * 71))
        canto = w.textura(f"{bid}_canto", tex_canto(sombra(c, -0.05)))
        _formas(w, bid, tex, FORMAS_REJILLA,
                f"Rejilla de {_minuscula(met_es)}", f"{met_en} Grating",
                canto=canto)
        salida.append(Material(bid, "REJILLA", mapa, FORMAS_REJILLA))
    return salida


def _pavimento(w: Escritor) -> list[Material]:
    salida = []
    for i, (pid, p_es, p_en, hexa, dibujo, mapa) in enumerate(PAVIMENTOS):
        bid = f"pavimento_{pid}"
        tex = w.textura(bid, DIBUJOS_PAVIMENTO[dibujo](rgb(hexa), 5000 + i * 61))
        _formas(w, bid, tex, FORMAS_PAVIMENTO, p_es, p_en)
        salida.append(Material(bid, "PAVIMENTO", mapa, FORMAS_PAVIMENTO))
    return salida
