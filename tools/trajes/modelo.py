# -*- coding: utf-8 -*-
"""
EL MODELO DE UN TRAJE: cubos, huesos, textura y el .geo.json de GeckoLib.

Un traje NO es una imagen. Son dos cosas:

  la FORMA     un .geo.json que dice «aqui va un cubo de 3x2x3 encima de la
               cabeza» (las orejas), «aqui una capa», «aqui unas hombreras»
  la PINTURA   un PNG que se estira por encima de esos cubos

⚠⚠⚠ LOS NOMBRES DE LOS HUESOS SON LA CONVENCION DE GECKOLIB, NO UNA ELECCION.
   `bipedHead`, `bipedBody`, `bipedRightArm`... son anclas VACIAS que GeckoLib
   pega al cuerpo del jugador; dentro van los `armor*` con los cubos de verdad.
   Un nombre mal escrito NO DA NINGUN ERROR: el traje sale flotando en el
   origen del mundo, o no sale. Verificado abriendo el mod de Diosesmon
   (`assets/diosesmon-client/geo/eevee/`), y coincide con la wiki de GeckoLib.

⚠⚠ Y LOS PIVOTES DE `armorHead` Y `armorBody` VAN A [0, 24, 0]. Lo dice su wiki
   y es lo que mas facil se escapa: con otro pivote el traje gira alrededor del
   punto equivocado y la cabeza se descoloca al mirar arriba.
"""

from __future__ import annotations

import json
import math
import random
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image

# ---------------------------------------------------------------- el esqueleto

# ⚠ Sale del modelo de jugador de vanilla, no de la nada: si el pivote no
#   coincide con el del hueso al que GeckoLib lo pega, el traje se mueve raro.
PIVOTES = {
    "bipedHead": (0, 24, 0),
    "bipedBody": (0, 24, 0),
    "bipedRightArm": (-5, 22, 0),
    "bipedLeftArm": (5, 22, 0),
    "bipedRightLeg": (-1.9, 12, 0),
    "bipedLeftLeg": (1.9, 12, 0),
}

# Cada hueso de armadura cuelga de su ancla. Las botas cuelgan de la pierna:
# asi una sola pieza puede llevar espinillera y bota y se mueven juntas.
PADRES = {
    "armorHead": "bipedHead",
    "armorBody": "bipedBody",
    "armorRightArm": "bipedRightArm",
    "armorLeftArm": "bipedLeftArm",
    "armorRightLeg": "bipedRightLeg",
    "armorLeftLeg": "bipedLeftLeg",
    "armorRightBoot": "armorRightLeg",
    "armorLeftBoot": "armorLeftLeg",
}

# Que huesos puede tocar cada pieza. Es lo que impide que el peto dibuje una
# bota: en el juego son cuatro objetos distintos y cada uno se pone por su lado.
PIEZAS = {
    "head": ["armorHead"],
    "body": ["armorBody", "armorRightArm", "armorLeftArm"],
    "legs": ["armorRightLeg", "armorLeftLeg"],
    "boots": ["armorRightBoot", "armorLeftBoot"],
}


def pivote_de(hueso):
    """El pivote de un hueso de armadura: el mismo que el de su ancla."""
    ancla = PADRES.get(hueso, hueso)
    while ancla in PADRES:
        ancla = PADRES[ancla]
    return PIVOTES[ancla]


# ---------------------------------------------------------------- los cubos

@dataclass
class Cubo:
    """Un cubo del traje, en coordenadas de Minecraft (el suelo es y=0)."""

    origen: tuple            # esquina minima (x, y, z)
    tam: tuple               # (dx, dy, dz)
    color: tuple             # RGB base
    material: str = "tela"   # tela / metal / cuero / neon
    inflate: float = 0.0
    uv: tuple = field(default=None)   # lo asigna empaquetar()

    def huella(self):
        """Lo que ocupa en la textura: (2*fondo + 2*ancho) x (fondo + alto)."""
        w, h, d = (max(1, int(math.ceil(v))) for v in self.tam)
        return (2 * d + 2 * w, d + h)


@dataclass
class Traje:
    """Un traje entero: cuatro piezas, cada una con sus huesos y sus cubos."""

    id: str
    nombre: str
    huesos: dict = field(default_factory=dict)   # hueso -> [Cubo]

    def poner(self, hueso, *cubos):
        self.huesos.setdefault(hueso, []).extend(cubos)

    def de_pieza(self, pieza):
        """Los huesos de una pieza, sin los vacios."""
        return {h: self.huesos[h] for h in PIEZAS[pieza] if self.huesos.get(h)}


# ---------------------------------------------------------------- la textura

# ⚠⚠ EL REPARTO DE LA TEXTURA SE CALCULA, NO SE ESCRIBE. Es exactamente el
#    fallo que ya nos costo caro en las pantallas: unas coordenadas escritas a
#    mano cuadran hasta que alguien cambia un cubo, y entonces DOS CUBOS
#    COMPARTEN PIXELES -- que no da ningun error, da un traje con la cara de la
#    bota dibujada en el hombro.
def empaquetar(cubos, lado=128):
    """
    Reparte los cubos por la textura y les escribe su `uv`.

    Estanteria simple: se ordenan por altura y se van llenando filas.
    Devuelve el alto usado, o lanza si no caben.
    """
    orden = sorted(cubos, key=lambda c: -c.huella()[1])
    x = y = fila_alta = 0
    for c in orden:
        w, h = c.huella()
        if w > lado:
            raise ValueError("un cubo de %s no cabe en %d px de ancho"
                             % (c.tam, lado))
        if x + w > lado:
            x, y, fila_alta = 0, y + fila_alta, 0
        if y + h > lado:
            raise ValueError("los cubos no caben en %dx%d: hacen falta mas de "
                             "%d px de alto" % (lado, lado, y + h))
        c.uv = (x, y)
        x += w
        fila_alta = max(fila_alta, h)
    return y + fila_alta


def _mezcla(color, delta):
    return tuple(max(0, min(255, int(c + delta))) for c in color)


def _pintar_cara(px, ox, oy, ancho, alto, cubo, brillo, rng):
    """
    Pinta una cara.

    ⚠ El BORDE de 1 px mas oscuro no es decoracion: sin el, dos cubos pegados
      del mismo color se ven como un bloque unico y el traje pierde la forma.
      Es la misma razon por la que los iconos del mercado llevan trazo grueso.
    """
    base = _mezcla(cubo.color, brillo)
    for j in range(alto):
        for i in range(ancho):
            c = base
            if cubo.material == "tela":
                c = _mezcla(c, rng.randint(-7, 7))
                if j % 3 == 0:
                    c = _mezcla(c, -5)
            elif cubo.material == "metal":
                # una veta vertical clara, que es lo que lee como metal
                c = _mezcla(c, 12 if i == ancho // 3 else rng.randint(-4, 4))
            elif cubo.material == "cuero":
                c = _mezcla(c, rng.randint(-12, 6))
            elif cubo.material == "neon":
                c = cubo.color   # plano: lo que brilla no lleva sombra
            borde = i == 0 or j == 0 or i == ancho - 1 or j == alto - 1
            if borde and cubo.material != "neon":
                c = _mezcla(c, -34)
            px[ox + i, oy + j] = (c[0], c[1], c[2], 255)


# Cuanto se aclara u oscurece cada cara. Sin esto el traje es una silueta
# plana: el volumen de un cubo se ve porque su tapa esta mas clara que su lado.
BRILLO = {"arriba": 22, "abajo": -30, "frente": 6, "espalda": -12,
          "derecha": -6, "izquierda": -6}


def _caras(cubo):
    """
    Donde cae cada cara dentro de la huella del cubo.

    ⚠⚠ ESTE REPARTO NO ES LIBRE: es el de Minecraft, y se verifico contra la
       cabeza de vanilla -- cubo 8x8x8 en uv [0,0], y la cara va a (8,8). Si el
       generador y el juego no estuvieran de acuerdo, la textura saldria rotada
       o cambiada de cara, y solo se veria mirandolo.
    """
    u, v = cubo.uv
    w, h, d = (max(1, int(math.ceil(t))) for t in cubo.tam)
    return [
        (u, v + d, d, h, "derecha"),                 # -X, la derecha del modelo
        (u + d, v + d, w, h, "frente"),              # -Z, la cara
        (u + d + w, v + d, d, h, "izquierda"),       # +X
        (u + 2 * d + w, v + d, w, h, "espalda"),     # +Z
        (u + d, v, w, d, "arriba"),                  # +Y
        (u + d + w, v, w, d, "abajo"),               # -Y
    ]


def pintar(cubos, lado=128, semilla=0):
    img = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(semilla)
    for c in cubos:
        for ox, oy, aw, ah, cara in _caras(c):
            _pintar_cara(px, ox, oy, aw, ah, c, BRILLO[cara], rng)
    return img


def mascara_brillo(cubos, lado=128):
    """
    La capa que brilla de noche, solo con los cubos `neon`.

    ⚠ Devuelve None si no hay ninguno: un glowmask completamente negro no es
      «no brilla», es un fichero mas que cargar y una via de que algo brille
      por error.
    """
    neones = [c for c in cubos if c.material == "neon"]
    if not neones:
        return None
    img = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(0)
    for c in neones:
        for ox, oy, aw, ah, _ in _caras(c):
            _pintar_cara(px, ox, oy, aw, ah, c, 0, rng)
    return img


# ---------------------------------------------------------------- el .geo.json

def geo(traje, pieza, lado=128):
    huesos = traje.de_pieza(pieza)
    anclas, salida = [], []
    for hueso in huesos:
        padre = PADRES[hueso]
        if padre.startswith("biped") and padre not in anclas:
            anclas.append(padre)
    for a in anclas:
        salida.append({"name": a, "pivot": list(PIVOTES[a])})
    for hueso, cubos in huesos.items():
        b = {"name": hueso, "parent": PADRES[hueso],
             "pivot": list(pivote_de(hueso)), "cubes": []}
        for c in cubos:
            cubo = {"origin": [round(v, 4) for v in c.origen],
                    "size": [round(v, 4) for v in c.tam],
                    "uv": list(c.uv)}
            if c.inflate:
                cubo["inflate"] = c.inflate
            b["cubes"].append(cubo)
        salida.append(b)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.unknown",
                "texture_width": lado, "texture_height": lado,
                "visible_bounds_width": 3, "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.25, 0],
            },
            "bones": salida,
        }],
    }


def escribir(traje, destino, lado=128):
    """Genera las cuatro piezas: .geo.json + textura (+ glowmask si toca)."""
    hechos = []
    geo_dir = Path(destino) / "geo" / traje.id
    tex_dir = Path(destino) / "textures" / "armor" / traje.id
    geo_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for pieza in PIEZAS:
        huesos = traje.de_pieza(pieza)
        if not huesos:
            continue
        cubos = [c for lista in huesos.values() for c in lista]
        alto = empaquetar(cubos, lado)
        (geo_dir / ("%s_%s.geo.json" % (traje.id, pieza))).write_text(
            json.dumps(geo(traje, pieza, lado), indent=2), encoding="utf-8")
        semilla = sum(ord(ch) for ch in traje.id + pieza)
        pintar(cubos, lado, semilla).save(
            tex_dir / ("%s_%s.png" % (traje.id, pieza)))
        brillo = mascara_brillo(cubos, lado)
        if brillo:
            brillo.save(tex_dir / ("%s_%s_glowmask.png" % (traje.id, pieza)))
        hechos.append((pieza, len(cubos), alto, brillo is not None))
    return hechos
